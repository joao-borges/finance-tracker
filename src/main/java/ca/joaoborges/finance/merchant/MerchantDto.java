package ca.joaoborges.finance.merchant;

import lombok.Builder;

/**
 * Request/response DTO for merchants. {@code logoUrl} is derived from
 * {@code website}, not set by the client.
 */
@Builder
public record MerchantDto(
        Long id,
        String name,
        String icon,
        String website,
        String logoUrl) {
}
