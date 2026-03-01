package com.wallet.txhistory.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.time.OffsetDateTime;

@Value.Immutable
@WalletStyle
@JsonDeserialize(as = ImmutableSyncRequest.class)
public interface SyncRequest {
    @Nullable Integer lookbackDays();
    @Nullable OffsetDateTime startTime();
    @Nullable OffsetDateTime endTime();
}
