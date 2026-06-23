package ca.joaoborges.finance.rule;

import lombok.Builder;

import java.time.Instant;

/**
 * Request/response DTO for rules. Writes resolve {@code categoryId} (required)
 * and, optionally, a merchant to associate — either an existing
 * {@code merchantId} or a {@code newMerchantName} created on the fly.
 */
@Builder
public record RuleDto(
        Long id,
        String name,
        String merchantMatch,
        Long categoryId,
        String categoryName,
        Long merchantId,
        String merchantName,
        String newMerchantName,
        Boolean autoApprove,
        Integer priority,
        Boolean enabled,
        Long matchCount,
        Instant lastMatchedAt) {
}
