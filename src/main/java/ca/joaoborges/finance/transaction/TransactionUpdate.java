package ca.joaoborges.finance.transaction;

/**
 * Inline edit of a single transaction from the list. Partial: only non-null
 * fields are applied. {@code merchantId}/{@code newMerchantName} link an existing
 * or freshly-created merchant; {@code needsReview} approves; {@code
 * excludedFromBudget} toggles whether the row counts toward budgets.
 */
public record TransactionUpdate(
        Long categoryId,
        Long merchantId,
        String newMerchantName,
        Boolean needsReview,
        Boolean excludedFromBudget,
        Boolean awaitingRefund) {
}
