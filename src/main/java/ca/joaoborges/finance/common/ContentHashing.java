package ca.joaoborges.finance.common;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/**
 * Content hash used for cross-source dedup: {@code account-key + posted-date
 * (UTC) + amount + normalized-merchant}. The account key is the database id
 * (as a string), NOT the display name — names get renamed and accounts get
 * merged, and a hash keyed on the name silently stops matching afterwards
 * (learned the hard way, twice). The date is the UTC posting day, so a
 * SimpleFIN {@code posted} timestamp and a CSV date column for the same charge
 * align. See the dedup notes in {@code DESIGN.md}.
 */
public final class ContentHashing {

    public static String of(final String accountKey, final Instant postedAt,
                            final BigDecimal amount, final String merchant) {
        final LocalDate day = postedAt == null ? LocalDate.EPOCH : postedAt.atZone(ZoneOffset.UTC).toLocalDate();
        final String canonical = normalize(accountKey)
                + '|' + day
                + '|' + amount.setScale(4, java.math.RoundingMode.HALF_UP).toPlainString()
                + '|' + normalize(merchant);
        return sha256Hex(canonical);
    }

    /**
     * Statement hash: like {@link #of} but keyed on the RAW statement text with
     * aggressive normalization (lowercase, alphanumerics only), so the same
     * charge hashes identically whether the text comes from a bank CSV export or
     * the SimpleFIN bridge's {@code description} — the two differ in spacing and
     * punctuation but not in substance. The merchant-based content hash can't
     * bridge that gap because SimpleFIN's payee is a derived, prettified name.
     */
    public static String ofStatement(final String accountKey, final Instant postedAt,
                                     final BigDecimal amount, final String statementText) {
        final LocalDate day = postedAt == null ? LocalDate.EPOCH : postedAt.atZone(ZoneOffset.UTC).toLocalDate();
        return statementForDay(accountKey, day, amount, statementText);
    }

    /**
     * Statement hashes for the posting day and its ±{@code days} neighbours.
     * Banks skew dates across sources — a CSV export carries the transaction
     * date while the bridge reports the (later) posted date — so cross-source
     * dedup probes nearby days instead of trusting exact-day equality.
     */
    public static List<String> statementProbes(final String accountKey, final Instant postedAt,
                                               final BigDecimal amount, final String statementText,
                                               final int days) {
        final LocalDate day = postedAt == null ? LocalDate.EPOCH : postedAt.atZone(ZoneOffset.UTC).toLocalDate();
        final List<String> probes = new ArrayList<>();
        for (int offset = -days; offset <= days; offset++) {
            probes.add(statementForDay(accountKey, day.plusDays(offset), amount, statementText));
        }
        return probes;
    }

    private static String statementForDay(final String accountKey, final LocalDate day,
                                          final BigDecimal amount, final String statementText) {
        final String canonical = normalize(accountKey)
                + '|' + day
                + '|' + amount.setScale(4, java.math.RoundingMode.HALF_UP).toPlainString()
                + '|' + normalizeStatement(statementText);
        return sha256Hex(canonical);
    }

    private static String normalizeStatement(final String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static String normalize(final String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static String sha256Hex(final String input) {
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            final byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (final NoSuchAlgorithmException wrapped) {
            throw new IllegalStateException("SHA-256 not available", wrapped);
        }
    }

    private ContentHashing() {
    }

}
