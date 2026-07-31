package ca.joaoborges.finance.institution;

import lombok.Builder;

/**
 * Request/response DTO for institutions. On PATCH, only non-null fields are
 * applied; {@code logoUrl} is derived from {@code website}.
 */
@Builder
public record InstitutionDto(
        Long id,
        String name,
        String website,
        String logoUrl,
        Boolean offBudget) {
}
