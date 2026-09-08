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
 *
 * <p>{@code clearMerchant} unlinks the canonical merchant so the row falls back
 * to the raw statement text. It needs its own flag because a null
 * {@code merchantId} already means "leave the merchant alone" under PATCH
 * semantics — without it, a merchant an over-broad rule attached can never be
 * taken off.
 */
public record TransactionUpdate(
        Long categoryId,
        Long merchantId,
        String newMerchantName,
        Boolean clearMerchant,
        Boolean needsReview,
        Boolean excludedFromBudget,
        Boolean awaitingRefund,
        LocalDate postedAt,
        String timeZone,
        List<String> tags) {
}
