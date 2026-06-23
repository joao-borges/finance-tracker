package ca.joaoborges.finance.csv;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One parsed CSV row, normalized across formats before it enters the ingest
 * pipeline. {@code amount} is signed (negative = outflow). {@code postedAt} is
 * null when the source has no date (the simple format) — ingest defaults it.
 */
public record ParsedTransaction(String accountName, String merchantName, BigDecimal amount, Instant postedAt) {
}
