package ca.joaoborges.finance.transaction;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * Combinable transactions-list filter, bound from query params. Account,
 * merchant and category accept multiple ids (comma-separated). Every field is
 * optional; {@link #toSpecification()} folds the set ones together with the
 * always-on visible predicate.
 */
public record TransactionFilter(
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        List<Long> accountIds,
        List<Long> merchantIds,
        List<Long> categoryIds,
        Boolean review) {

    public Specification<Transaction> toSpecification() {
        final List<Specification<Transaction>> specs = new ArrayList<>();
        specs.add(TransactionSpecs.visible());
        if (from != null) {
            specs.add(TransactionSpecs.postedFrom(from.atStartOfDay(ZoneOffset.UTC).toInstant()));
        }
        if (to != null) {
            specs.add(TransactionSpecs.postedBefore(to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()));
        }
        if (accountIds != null && !accountIds.isEmpty()) {
            specs.add(TransactionSpecs.accountIn(accountIds));
        }
        if (merchantIds != null && !merchantIds.isEmpty()) {
            specs.add(TransactionSpecs.merchantIn(merchantIds));
        }
        if (categoryIds != null && !categoryIds.isEmpty()) {
            specs.add(TransactionSpecs.categoryIn(categoryIds));
        }
        if (review != null) {
            specs.add(TransactionSpecs.needsReview(review));
        }
        return specs.stream().reduce(Specification::and).orElse(null);
    }

}
