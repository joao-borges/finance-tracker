package ca.joaoborges.finance.amazon;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Pairs Amazon invoice charges with the bank transactions they became. Pure —
 * no JPA — so the rules are testable in isolation.
 *
 * <p>A charge matches an outflow of exactly its amount whose statement looks
 * like Amazon, posted from {@link #DAYS_BEFORE} days before to
 * {@link #DAYS_AFTER} days after the invoice's charge date (cards post a few
 * days late; the bridge occasionally dates a row a day early). Among several
 * fits: a still-unlinked transaction beats one already claimed, then an account
 * whose name carries the card's last four digits, then the nearest date, then
 * posting on/after the charge, then the lowest id — deterministic, so
 * re-running is idempotent.
 *
 * <p>A transaction already linked to a charge with the SAME key (date, amount,
 * card) stays eligible: that is one card charge covering several orders, which
 * each list it on their own invoice. Two same-day identical charges for two
 * orders still spread over two transactions, because unlinked rows win.
 */
public final class AmazonChargeMatcher {

    static final int DAYS_BEFORE = 3;
    static final int DAYS_AFTER = 7;

    private static final List<String> AMAZON_MARKERS = List.of("amazon", "amzn");

    private AmazonChargeMatcher() {
    }

    /** What identifies a charge on the card statement, whichever order's invoice listed it. */
    public record ChargeKey(LocalDate chargedOn, BigDecimal amount, String cardLast4) {

        public ChargeKey {
            // Invoice amounts arrive at scale 2, stored ones at scale 4 — compare by value.
            amount = amount == null ? null : amount.setScale(2, RoundingMode.HALF_UP);
        }

    }

    public record PendingCharge(long id, ChargeKey key) {
    }

    /** A bank transaction that could be an Amazon charge; {@code amount} is signed (outflow negative). */
    public record Candidate(long transactionId, LocalDate postedOn, BigDecimal amount, String accountLabel) {
    }

    /** True when any descriptor (raw statement or canonical merchant) reads as Amazon. */
    public static boolean isAmazon(final String... descriptors) {
        for (final String descriptor : descriptors) {
            if (descriptor == null) {
                continue;
            }
            final String lower = descriptor.toLowerCase(Locale.ROOT);
            if (AMAZON_MARKERS.stream().anyMatch(lower::contains)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Match {@code charges} (in the given order — pass them oldest first)
     * against {@code candidates}. {@code linked} holds the keys of charges
     * already linked to each transaction id; it is not modified.
     *
     * @return charge id → transaction id, for the charges that found a match
     */
    public static Map<Long, Long> match(final List<PendingCharge> charges,
                                        final List<Candidate> candidates,
                                        final Map<Long, Set<ChargeKey>> linked) {
        final Map<Long, Set<ChargeKey>> claims = new HashMap<>();
        linked.forEach((transactionId, keys) -> claims.put(transactionId, new HashSet<>(keys)));

        final Map<Long, Long> result = new LinkedHashMap<>();
        for (final PendingCharge charge : charges) {
            pick(charge.key(), candidates, claims).ifPresent(candidate -> {
                result.put(charge.id(), candidate.transactionId());
                claims.computeIfAbsent(candidate.transactionId(), ignored -> new HashSet<>()).add(charge.key());
            });
        }
        return result;
    }

    private static Optional<Candidate> pick(final ChargeKey key,
                                            final List<Candidate> candidates,
                                            final Map<Long, Set<ChargeKey>> claims) {
        final Comparator<Candidate> preference = Comparator
                .comparingInt((Candidate candidate) -> claims.containsKey(candidate.transactionId()) ? 1 : 0)
                .thenComparingInt(candidate -> carriesCard(candidate, key) ? 0 : 1)
                .thenComparingLong(candidate -> Math.abs(dayOffset(key, candidate)))
                .thenComparingInt(candidate -> dayOffset(key, candidate) < 0 ? 1 : 0)
                .thenComparingLong(Candidate::transactionId);
        return candidates.stream()
                .filter(candidate -> fits(key, candidate))
                .filter(candidate -> {
                    final Set<ChargeKey> taken = claims.get(candidate.transactionId());
                    return taken == null || taken.stream().allMatch(key::equals);
                })
                .min(preference);
    }

    private static boolean fits(final ChargeKey key, final Candidate candidate) {
        if (candidate.amount() == null || candidate.amount().negate().compareTo(key.amount()) != 0) {
            return false;
        }
        final long offset = dayOffset(key, candidate);
        return offset >= -DAYS_BEFORE && offset <= DAYS_AFTER;
    }

    private static long dayOffset(final ChargeKey key, final Candidate candidate) {
        return ChronoUnit.DAYS.between(key.chargedOn(), candidate.postedOn());
    }

    private static boolean carriesCard(final Candidate candidate, final ChargeKey key) {
        return key.cardLast4() != null && candidate.accountLabel() != null
                && candidate.accountLabel().contains(key.cardLast4());
    }

}
