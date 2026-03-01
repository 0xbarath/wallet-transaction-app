package com.wallet.txhistory.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import com.wallet.txhistory.dto.ImmutableSyncResponse;
import com.wallet.txhistory.dto.SyncRequest;
import com.wallet.txhistory.dto.SyncResponse;
import com.wallet.txhistory.exception.RateLimitExceededException;
import com.wallet.txhistory.exception.SyncInProgressException;
import com.wallet.txhistory.exception.WalletNotFoundException;
import com.wallet.txhistory.model.Direction;
import com.wallet.txhistory.model.Transfer;
import com.wallet.txhistory.model.TransferCategory;
import com.wallet.txhistory.model.Wallet;
import com.wallet.txhistory.model.WalletSyncState;
import com.wallet.txhistory.repository.TransferRepository;
import com.wallet.txhistory.repository.WalletRepository;
import com.wallet.txhistory.repository.WalletSyncStateRepository;
import com.wallet.txhistory.alchemy.AlchemyClient;
import com.wallet.txhistory.alchemy.AlchemyProperties;
import com.wallet.txhistory.alchemy.AlchemyTransfer;
import com.wallet.txhistory.alchemy.AlchemyTransferPage;
import com.wallet.txhistory.config.TokenBucketRateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class SyncService {

    private static final Logger log = LoggerFactory.getLogger(SyncService.class);
    private static final int DEFAULT_LOOKBACK_DAYS = 30;
    private static final long ESTIMATED_CURRENT_BLOCK = 19_000_000L;
    private static final long BLOCKS_PER_DAY = 7_200L;

    private final WalletRepository walletRepository;
    private final WalletSyncStateRepository syncStateRepository;
    private final TransferRepository transferRepository;
    private final AlchemyClient alchemyClient;
    private final AlchemyProperties alchemyProperties;
    private final TokenBucketRateLimiter rateLimiter;
    private final Timer syncTimer;
    private final Counter transfersSyncedCounter;

    public SyncService(WalletRepository walletRepository,
                       WalletSyncStateRepository syncStateRepository,
                       TransferRepository transferRepository,
                       AlchemyClient alchemyClient,
                       AlchemyProperties alchemyProperties,
                       TokenBucketRateLimiter rateLimiter,
                       MeterRegistry meterRegistry) {
        this.walletRepository = walletRepository;
        this.syncStateRepository = syncStateRepository;
        this.transferRepository = transferRepository;
        this.alchemyClient = alchemyClient;
        this.alchemyProperties = alchemyProperties;
        this.rateLimiter = rateLimiter;
        this.syncTimer = Timer.builder("wallet.sync.duration")
                .description("Wallet sync duration")
                .register(meterRegistry);
        this.transfersSyncedCounter = Counter.builder("wallet.sync.transfers")
                .description("Transfers synced")
                .register(meterRegistry);
    }

    public SyncResponse sync(UUID walletId, SyncRequest request) {
        if (!rateLimiter.tryConsume(walletId)) {
            throw new RateLimitExceededException("Rate limit exceeded for wallet: " + walletId);
        }

        Timer.Sample sample = Timer.start();

        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new WalletNotFoundException(walletId));

        WalletSyncState syncState = acquireSyncLock(walletId);

        try {
            String fromBlock = computeFromBlock(syncState, request);
            String toBlock = "latest";

            List<Transfer> allTransfers = new ArrayList<>();
            long maxBlock = syncState.getLastSyncedBlock() != null ? syncState.getLastSyncedBlock() : 0L;

            // Fetch outgoing transfers
            List<Transfer> outTransfers = fetchTransfers(wallet, Direction.OUT, fromBlock, toBlock);
            allTransfers.addAll(outTransfers);

            // Fetch incoming transfers
            List<Transfer> inTransfers = fetchTransfers(wallet, Direction.IN, fromBlock, toBlock);
            allTransfers.addAll(inTransfers);

            // Track max block
            for (Transfer t : allTransfers) {
                if (t.getBlockNum() != null && t.getBlockNum() > maxBlock) {
                    maxBlock = t.getBlockNum();
                }
            }

            // Dedup against existing records and save new ones
            int inserted = deduplicateAndSave(allTransfers, wallet.getNetwork());

            // Update sync state
            WalletSyncState updated = releaseSyncLock(walletId, maxBlock);

            transfersSyncedCounter.increment(inserted);
            sample.stop(syncTimer);

            log.info("Sync completed for wallet {} - {} transfers synced", walletId, inserted);

            return ImmutableSyncResponse.builder()
                    .walletId(walletId)
                    .status("completed")
                    .transfersSynced(inserted)
                    .lastSyncedBlock(updated != null ? updated.getLastSyncedBlock() : null)
                    .lastSyncedAt(updated != null ? updated.getLastSyncedAt() : null)
                    .build();
        } catch (Exception e) {
            releaseSyncLock(walletId, null);
            throw e;
        }
    }

    @Transactional
    protected int deduplicateAndSave(List<Transfer> transfers, String network) {
        if (transfers.isEmpty()) {
            return 0;
        }

        List<String> candidateIds = transfers.stream()
                .map(Transfer::getUniqueId)
                .toList();

        Set<String> existingIds = transferRepository.findExistingUniqueIds(network, candidateIds);

        List<Transfer> newTransfers = transfers.stream()
                .filter(t -> !existingIds.contains(t.getUniqueId()))
                .toList();

        if (!newTransfers.isEmpty()) {
            transferRepository.saveAll(newTransfers);
        }

        return newTransfers.size();
    }

    @Transactional
    protected WalletSyncState acquireSyncLock(UUID walletId) {
        WalletSyncState syncState = syncStateRepository.findById(walletId)
                .orElseThrow(() -> new WalletNotFoundException(walletId));

        if (syncState.isSyncInProgress()) {
            throw new SyncInProgressException(walletId);
        }

        try {
            syncState.setSyncInProgress(true);
            return syncStateRepository.save(syncState);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new SyncInProgressException(walletId);
        }
    }

    @Transactional
    protected WalletSyncState releaseSyncLock(UUID walletId, Long maxBlock) {
        WalletSyncState state = syncStateRepository.findById(walletId).orElse(null);
        if (state == null) {
            log.warn("No sync state found for wallet {} during lock release", walletId);
            return null;
        }
        state.setSyncInProgress(false);
        if (maxBlock != null) {
            state.setLastSyncedBlock(maxBlock);
            state.setLastSyncedAt(OffsetDateTime.now());
        }
        return syncStateRepository.save(state);
    }

    private List<Transfer> fetchTransfers(Wallet wallet, Direction direction, String fromBlock, String toBlock) {
        List<Transfer> transfers = new ArrayList<>();
        String pageKey = null;
        String alchemyDirection = direction == Direction.OUT ? "OUT" : "IN";

        do {
            AlchemyTransferPage page = alchemyClient.getAssetTransfers(
                    wallet.getAddress(), alchemyDirection, fromBlock, toBlock,
                    alchemyProperties.categories(), pageKey);

            for (AlchemyTransfer at : page.transfers()) {
                transfers.add(mapToTransfer(at, wallet, direction));
            }

            pageKey = page.hasMore() ? page.pageKey() : null;
        } while (pageKey != null);

        return transfers;
    }

    private Transfer mapToTransfer(AlchemyTransfer at, Wallet wallet, Direction direction) {
        Transfer t = new Transfer();
        t.setWalletId(wallet.getId());
        t.setNetwork(wallet.getNetwork());
        t.setUniqueId(at.uniqueId());
        t.setHash(at.hash());
        t.setBlockNum(at.blockNumAsLong());
        t.setFromAddr(at.from() != null ? at.from().toLowerCase(Locale.ROOT) : "");
        t.setToAddr(at.to() != null ? at.to().toLowerCase(Locale.ROOT) : null);
        t.setDirection(direction);
        t.setAsset(at.asset());
        t.setCategory(mapCategory(at.category()));
        if (at.value() != null) {
            t.setValueDecimal(BigDecimal.valueOf(at.value()));
        }
        t.setRawValue(at.rawContractValue());
        t.setRawContractAddr(at.rawContractAddress());
        t.setRawContractDecimals(at.rawContractDecimals());
        t.setTokenId(at.tokenId());

        if (at.metadata() != null && at.metadata().get("blockTimestamp") != null) {
            try {
                t.setBlockTs(OffsetDateTime.parse((String) at.metadata().get("blockTimestamp")));
            } catch (Exception e) {
                // ignore unparseable timestamp
            }
        }

        return t;
    }

    private TransferCategory mapCategory(String category) {
        if (category == null) return TransferCategory.EXTERNAL;
        return switch (category.toLowerCase(Locale.ROOT)) {
            case "internal" -> TransferCategory.INTERNAL;
            case "erc20" -> TransferCategory.ERC20;
            case "erc721" -> TransferCategory.ERC721;
            case "erc1155" -> TransferCategory.ERC1155;
            default -> TransferCategory.EXTERNAL;
        };
    }

    private String computeFromBlock(WalletSyncState syncState, SyncRequest request) {
        if (request != null && request.startTime() != null) {
            long secondsAgo = java.time.Duration.between(request.startTime(), OffsetDateTime.now()).getSeconds();
            long blocksAgo = secondsAgo / 12;
            return toHexBlock(Math.max(0, ESTIMATED_CURRENT_BLOCK - blocksAgo));
        }

        if (request != null && request.lookbackDays() != null) {
            long blocksBack = request.lookbackDays() * BLOCKS_PER_DAY;
            return toHexBlock(Math.max(0, ESTIMATED_CURRENT_BLOCK - blocksBack));
        }

        if (syncState.getLastSyncedBlock() != null && syncState.getLastSyncedBlock() > 0) {
            return toHexBlock(syncState.getLastSyncedBlock() + 1);
        }

        long blocksBack = DEFAULT_LOOKBACK_DAYS * BLOCKS_PER_DAY;
        return toHexBlock(Math.max(0, ESTIMATED_CURRENT_BLOCK - blocksBack));
    }

    private static String toHexBlock(long blockNumber) {
        return "0x" + Long.toHexString(blockNumber);
    }
}
