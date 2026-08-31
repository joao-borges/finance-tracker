package ca.joaoborges.finance.transaction;

import ca.joaoborges.finance.match.MatchType;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Read DTO for the transactions list. {@code merchantName} is the raw bank
 * descriptor; {@code merchant} is the canonical merchant's name once linked.
 * Logo/icon fields drive the list's avatars and category emoji.
 */
@Builder
public record TransactionDto(
        Long id,
        Instant postedAt,
        Instant sourcePostedAt,
        boolean dateAdjusted,
        Long accountId,
        String accountName,
        String accountLogoUrl,
        boolean accountOffBudget,
        String merchantName,
        Long merchantId,
        String merchant,
        String merchantLogoUrl,
        String merchantIcon,
        Long categoryId,
        String categoryName,
        String categoryIcon,
        BigDecimal amount,
        String currency,
        String source,
        boolean needsReview,
        boolean excludedFromBudget,
        boolean awaitingRefund,
        boolean dedup,
        java.util.List<String> tags,
        MatchType matchType,
        Long matchedWithId,
        Long splitParentId) {
}
