package ca.joaoborges.finance.common;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Content hash used for cross-source dedup: {@code account + posted-date (UTC) +
 * amount + normalized-merchant}. Stable and order-independent so the same logical
 * transaction hashes identically whether it arrives via SimpleFIN or CSV. The
 * date is the UTC posting day, so a SimpleFIN {@code posted} timestamp and a CSV
 * date column for the same charge align. See the dedup notes in {@code DESIGN.md}.
 */
public final class ContentHashing {

    public static String of(final String accountName, final Instant postedAt,
                            final BigDecimal amount, final String merchant) {
        final LocalDate day = postedAt == null ? LocalDate.EPOCH : postedAt.atZone(ZoneOffset.UTC).toLocalDate();
        final String canonical = normalize(accountName)
                + '|' + day
                + '|' + amount.setScale(4, java.math.RoundingMode.HALF_UP).toPlainString()
                + '|' + normalize(merchant);
        return sha256Hex(canonical);
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
