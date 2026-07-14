package ca.joaoborges.finance.rule;

import lombok.Builder;

import java.time.Instant;

/**
 * Request/response DTO for rules. A rule's actions are any combination of a
 * category, a merchant (an existing {@code merchantId} or a
 * {@code newMerchantName} created on the fly), and auto-approve — at least one
 * is required on create.
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
