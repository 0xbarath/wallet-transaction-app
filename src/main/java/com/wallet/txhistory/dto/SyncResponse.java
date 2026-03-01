package com.wallet.txhistory.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.time.OffsetDateTime;
import java.util.UUID;

@Value.Immutable
@WalletStyle
@JsonDeserialize(as = ImmutableSyncResponse.class)
public interface SyncResponse {
    UUID walletId();
    String status();
    int transfersSynced();
    @Nullable Long lastSyncedBlock();
    @Nullable OffsetDateTime lastSyncedAt();
}
