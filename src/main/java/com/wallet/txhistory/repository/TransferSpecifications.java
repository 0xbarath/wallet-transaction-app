package com.wallet.txhistory.repository;

import com.wallet.txhistory.model.Direction;
import com.wallet.txhistory.model.Transfer;
import com.wallet.txhistory.model.TransferCategory;
import com.wallet.txhistory.model.QuerySpec;
import com.wallet.txhistory.model.TransactionCursor;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class TransferSpecifications {

    private TransferSpecifications() {}

    public static Specification<Transfer> fromQuerySpec(QuerySpec spec, boolean isAdmin) {
        List<Specification<Transfer>> specs = new ArrayList<>();

        specs.add(walletIdEquals(spec.walletId()));

        if (spec.direction() != null) {
            specs.add(directionEquals(spec.direction()));
        }

        List<TransferCategory> cats = resolveCategories(spec.categories(), isAdmin);
        if (!cats.isEmpty()) {
            specs.add(categoryIn(cats));
        } else if (!isAdmin) {
            specs.add(categoryNotInternal());
        }

        if (!spec.assets().isEmpty()) {
            specs.add(assetIn(spec.assets()));
        }

        if (spec.minValue() != null) {
            specs.add((root, query, cb) ->
                    cb.greaterThanOrEqualTo(root.get("valueDecimal"), spec.minValue()));
        }

        if (spec.maxValue() != null) {
            specs.add((root, query, cb) ->
                    cb.lessThanOrEqualTo(root.get("valueDecimal"), spec.maxValue()));
        }

        if (spec.counterparty() != null && !spec.counterparty().isBlank()) {
            String cp = spec.counterparty().toLowerCase();
            specs.add((root, query, cb) -> cb.or(
                    cb.equal(root.get("fromAddr"), cp),
                    cb.equal(root.get("toAddr"), cp)
            ));
        }

        if (spec.startTime() != null) {
            specs.add((root, query, cb) ->
                    cb.greaterThanOrEqualTo(root.get("createdAt"), spec.startTime()));
        }

        if (spec.endTime() != null) {
            specs.add((root, query, cb) ->
                    cb.lessThanOrEqualTo(root.get("createdAt"), spec.endTime()));
        }

        return Specification.allOf(specs);
    }

    public static Specification<Transfer> cursorPredicate(TransactionCursor cursor, boolean ascending) {
        return (root, query, cb) -> {
            if (ascending) {
                return cb.or(
                        cb.greaterThan(root.get("createdAt"), cursor.createdAt()),
                        cb.and(
                                cb.equal(root.get("createdAt"), cursor.createdAt()),
                                cb.greaterThan(root.get("id"), cursor.id())
                        )
                );
            } else {
                return cb.or(
                        cb.lessThan(root.get("createdAt"), cursor.createdAt()),
                        cb.and(
                                cb.equal(root.get("createdAt"), cursor.createdAt()),
                                cb.lessThan(root.get("id"), cursor.id())
                        )
                );
            }
        };
    }

    private static Specification<Transfer> walletIdEquals(UUID walletId) {
        return (root, query, cb) -> cb.equal(root.get("walletId"), walletId);
    }

    private static Specification<Transfer> directionEquals(Direction direction) {
        return (root, query, cb) -> cb.equal(root.get("direction"), direction);
    }

    private static Specification<Transfer> categoryIn(List<TransferCategory> categories) {
        return (root, query, cb) -> root.get("category").in(categories);
    }

    private static Specification<Transfer> categoryNotInternal() {
        return (root, query, cb) -> cb.notEqual(root.get("category"), TransferCategory.INTERNAL);
    }

    private static Specification<Transfer> assetIn(List<String> assets) {
        return (root, query, cb) -> root.get("asset").in(assets);
    }

    private static List<TransferCategory> resolveCategories(List<TransferCategory> requested, boolean isAdmin) {
        if (requested.isEmpty()) {
            return List.of();
        }
        if (isAdmin) {
            return requested;
        }
        return requested.stream()
                .filter(c -> c != TransferCategory.INTERNAL)
                .toList();
    }
}
