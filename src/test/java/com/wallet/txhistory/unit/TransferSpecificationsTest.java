package com.wallet.txhistory.unit;

import com.wallet.txhistory.model.Direction;
import com.wallet.txhistory.model.ImmutableQuerySpec;
import com.wallet.txhistory.model.TransferCategory;
import com.wallet.txhistory.model.QuerySpec;
import com.wallet.txhistory.repository.TransferSpecifications;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TransferSpecificationsTest {

    @Test
    void nonAdminExcludesInternalFromEmptyCategories() {
        QuerySpec spec = ImmutableQuerySpec.builder()
                .walletId(UUID.randomUUID())
                .build();
        Specification<?> result = TransferSpecifications.fromQuerySpec(spec, false);
        assertThat(result).isNotNull();
    }

    @Test
    void adminIncludesInternalWhenRequested() {
        QuerySpec spec = ImmutableQuerySpec.builder()
                .walletId(UUID.randomUUID())
                .categories(List.of(TransferCategory.INTERNAL))
                .build();
        Specification<?> result = TransferSpecifications.fromQuerySpec(spec, true);
        assertThat(result).isNotNull();
    }

    @Test
    void nonAdminStripsInternalFromRequestedCategories() {
        QuerySpec spec = ImmutableQuerySpec.builder()
                .walletId(UUID.randomUUID())
                .categories(List.of(TransferCategory.INTERNAL, TransferCategory.ERC20))
                .build();
        Specification<?> result = TransferSpecifications.fromQuerySpec(spec, false);
        assertThat(result).isNotNull();
    }

    @Test
    void createsSpecificationForAllFilters() {
        QuerySpec spec = ImmutableQuerySpec.builder()
                .walletId(UUID.randomUUID())
                .direction(Direction.OUT)
                .categories(List.of(TransferCategory.ERC20))
                .assets(List.of("USDC"))
                .minValue(BigDecimal.ONE)
                .maxValue(BigDecimal.TEN)
                .counterparty("0x1234567890abcdef1234567890abcdef12345678")
                .startTime(OffsetDateTime.now().minusDays(7))
                .endTime(OffsetDateTime.now())
                .build();
        Specification<?> result = TransferSpecifications.fromQuerySpec(spec, true);
        assertThat(result).isNotNull();
    }
}
