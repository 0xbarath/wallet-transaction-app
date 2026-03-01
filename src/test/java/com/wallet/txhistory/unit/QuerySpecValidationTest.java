package com.wallet.txhistory.unit;

import com.wallet.txhistory.model.ImmutableQuerySpec;
import com.wallet.txhistory.model.QuerySpec;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class QuerySpecValidationTest {

    @Test
    void defaultsLimitTo50() {
        QuerySpec spec = ImmutableQuerySpec.builder()
                .walletId(UUID.randomUUID())
                .limit(0)
                .build();
        assertThat(spec.limit()).isEqualTo(50);
    }

    @Test
    void capsLimitAt200() {
        QuerySpec spec = ImmutableQuerySpec.builder()
                .walletId(UUID.randomUUID())
                .limit(500)
                .build();
        assertThat(spec.limit()).isEqualTo(200);
    }

    @Test
    void defaultsSortToDesc() {
        QuerySpec spec = ImmutableQuerySpec.builder()
                .walletId(UUID.randomUUID())
                .build();
        assertThat(spec.sort()).isEqualTo(QuerySpec.SORT_CREATED_AT_DESC);
    }

    @Test
    void preservesExplicitSort() {
        QuerySpec spec = ImmutableQuerySpec.builder()
                .walletId(UUID.randomUUID())
                .sort("createdAt_asc")
                .build();
        assertThat(spec.sort()).isEqualTo("createdAt_asc");
    }

    @Test
    void nullCategoriesBecomesEmptyList() {
        QuerySpec spec = ImmutableQuerySpec.builder()
                .walletId(UUID.randomUUID())
                .build();
        assertThat(spec.categories()).isNotNull().isEmpty();
    }

    @Test
    void nullAssetsBecomesEmptyList() {
        QuerySpec spec = ImmutableQuerySpec.builder()
                .walletId(UUID.randomUUID())
                .build();
        assertThat(spec.assets()).isNotNull().isEmpty();
    }

    @Test
    void negativeLimitDefaultsTo50() {
        QuerySpec spec = ImmutableQuerySpec.builder()
                .walletId(UUID.randomUUID())
                .limit(-1)
                .build();
        assertThat(spec.limit()).isEqualTo(50);
    }
}
