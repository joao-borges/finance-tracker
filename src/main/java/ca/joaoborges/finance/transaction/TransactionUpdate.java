package ca.joaoborges.finance.transaction;

import java.time.LocalDate;
import java.util.List;

/**
 * Inline edit of a single transaction from the list. Partial: only non-null
 * fields are applied. {@code merchantId}/{@code newMerchantName} link an existing
 * or freshly-created merchant; {@code needsReview} approves; {@code
 * excludedFromBudget} toggles whether the row counts toward budgets;
 * {@code postedAt} moves the operator-visible date (budget month) while dedup
 * stays keyed on the source-reported date.
 */
public record TransactionUpdate(
        Long categoryId,
        Long merchantId,
        String newMerchantName,
        Boolean needsReview,
        Boolean excludedFromBudget,
        Boolean awaitingRefund,
        LocalDate postedAt,
        String timeZone,
        List<String> tags) {
}
