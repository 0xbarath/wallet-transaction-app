package com.wallet.txhistory.service;

import com.wallet.txhistory.dto.ImmutablePageResponse;
import com.wallet.txhistory.dto.ImmutableTransferResponse;
import com.wallet.txhistory.dto.PageResponse;
import com.wallet.txhistory.dto.TransferResponse;
import com.wallet.txhistory.exception.ForbiddenCategoryException;
import com.wallet.txhistory.filter.RbacFilter;
import com.wallet.txhistory.model.Transfer;
import com.wallet.txhistory.model.TransferCategory;
import com.wallet.txhistory.repository.TransferRepository;
import com.wallet.txhistory.model.QuerySpec;
import com.wallet.txhistory.model.TransactionCursor;
import com.wallet.txhistory.repository.TransferSpecifications;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TransactionQueryService {

    private final TransferRepository transferRepository;

    public TransactionQueryService(TransferRepository transferRepository) {
        this.transferRepository = transferRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<TransferResponse> query(QuerySpec spec, String role) {
        boolean isAdmin = RbacFilter.isAdmin(role);

        // RBAC: non-admin cannot request INTERNAL
        if (!isAdmin && spec.categories().contains(TransferCategory.INTERNAL)) {
            throw new ForbiddenCategoryException("INTERNAL transfers are only accessible to admins");
        }

        boolean ascending = QuerySpec.SORT_CREATED_AT_ASC.equals(spec.sort());
        Sort sort = ascending
                ? Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("id"))
                : Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

        Specification<Transfer> specification = TransferSpecifications.fromQuerySpec(spec, isAdmin);

        if (spec.cursor() != null && !spec.cursor().isBlank()) {
            TransactionCursor cursor = TransactionCursor.decode(spec.cursor());
            specification = specification.and(TransferSpecifications.cursorPredicate(cursor, ascending));
        }

        // Fetch limit + 1 to detect hasMore
        List<Transfer> results = transferRepository.findAll(specification,
                PageRequest.of(0, spec.limit() + 1, sort)).getContent();

        boolean hasMore = results.size() > spec.limit();
        List<Transfer> items = hasMore ? results.subList(0, spec.limit()) : results;

        String nextCursor = null;
        if (hasMore && !items.isEmpty()) {
            Transfer last = items.get(items.size() - 1);
            nextCursor = new TransactionCursor(last.getCreatedAt(), last.getId()).encode();
        }

        List<TransferResponse> responses = items.stream()
                .map(this::toResponse)
                .toList();

        return ImmutablePageResponse.<TransferResponse>builder()
                .items(responses)
                .nextCursor(nextCursor)
                .querySpec(spec)
                .build();
    }

    private TransferResponse toResponse(Transfer t) {
        return ImmutableTransferResponse.builder()
                .id(t.getId())
                .walletId(t.getWalletId())
                .network(t.getNetwork())
                .hash(t.getHash())
                .blockNum(t.getBlockNum())
                .blockTs(t.getBlockTs())
                .fromAddr(t.getFromAddr())
                .toAddr(t.getToAddr())
                .direction(t.getDirection().name())
                .asset(t.getAsset())
                .category(t.getCategory().name())
                .valueDecimal(t.getValueDecimal())
                .rawValue(t.getRawValue())
                .rawContractAddr(t.getRawContractAddr())
                .rawContractDecimals(t.getRawContractDecimals())
                .tokenId(t.getTokenId())
                .createdAt(t.getCreatedAt())
                .build();
    }
}
