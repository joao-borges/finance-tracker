package ca.joaoborges.finance.common;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Content hash used for cross-source dedup: {@code account + amount +
 * normalized-merchant} (date is folded in by callers that have one). Stable and
 * order-independent so the same logical transaction hashes identically whether
 * it arrives via SimpleFIN or CSV. See the dedup notes in {@code PLAN.md}.
 */
public final class ContentHashing {

    public static String of(final String accountName, final BigDecimal amount, final String merchant) {
        final String canonical = normalize(accountName)
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
