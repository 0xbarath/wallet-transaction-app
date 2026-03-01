package com.wallet.txhistory.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.time.OffsetDateTime;

@Value.Immutable
@WalletStyle
@JsonDeserialize(as = ImmutableSyncRequest.class)
public interface SyncRequest {
    @Nullable @Min(1) @Max(365) Integer lookbackDays();
    @Nullable OffsetDateTime startTime();
    @Nullable OffsetDateTime endTime();
}
