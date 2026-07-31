package ca.joaoborges.finance.account;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Request/response DTO for accounts. On create, {@code id} is null; on PATCH,
 * only non-null fields are applied. {@code logoUrl} is derived from
 * {@code website}, not set by the client.
 */
@Builder
public record AccountDto(
        Long id,
        String name,
        String importRef,
        AccountType type,
        String currency,
        BigDecimal balance,
        String website,
        String logoUrl,
        Boolean offBudget,
        Boolean hidden,
        Boolean archived,
        Long mergedIntoId,
        String mergedIntoName,
        Instant balanceDate,
        Instant lastSyncedAt) {
}
