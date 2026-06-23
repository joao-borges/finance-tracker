package ca.joaoborges.finance.transaction;

import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.List;

/**
 * Reusable JPA criteria for the transactions list. {@link #visible()} encodes
 * the flag-table list predicate (no split parents, no quarantined dups) and is
 * always applied; the rest are optional, combinable filters.
 */
public final class TransactionSpecs {

    public static Specification<Transaction> visible() {
        return (root, query, cb) -> cb.and(cb.isFalse(root.get("split")), cb.isFalse(root.get("dedup")));
    }

    public static Specification<Transaction> postedFrom(final Instant from) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("postedAt"), from);
    }

    public static Specification<Transaction> postedBefore(final Instant before) {
        return (root, query, cb) -> cb.lessThan(root.get("postedAt"), before);
    }

    public static Specification<Transaction> accountIn(final List<Long> accountIds) {
        return (root, query, cb) -> root.get("account").get("id").in(accountIds);
    }

    public static Specification<Transaction> merchantIn(final List<Long> merchantIds) {
        return (root, query, cb) -> root.get("merchant").get("id").in(merchantIds);
    }

    public static Specification<Transaction> categoryIn(final List<Long> categoryIds) {
        return (root, query, cb) -> root.get("category").get("id").in(categoryIds);
    }

    public static Specification<Transaction> needsReview(final boolean review) {
        return (root, query, cb) -> cb.equal(root.get("needsReview"), review);
    }

    private TransactionSpecs() {
    }

}
