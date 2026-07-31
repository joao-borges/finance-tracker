package ca.joaoborges.finance.rule;

import ca.joaoborges.finance.transaction.Transaction;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Comparator;
import java.util.Locale;
import java.util.Optional;

/**
 * The phase-1 rules engine: a transaction's raw {@code merchantName} is tested
 * (case-insensitive {@code contains}) against each enabled rule by ascending
 * priority; the first match wins. The matched rule sets the category, optionally
 * links a canonical merchant, and — when {@code autoApprove} — clears the review
 * flag.
 *
 * <p>Pure and side-effect-light by design so it can be exercised without a
 * database. {@link #firstMatch} sorts defensively, so callers may pass rules in
 * any order.
 */
@Service
public class RuleEngine {

    /**
     * The first enabled rule (by ascending priority, then encounter order) whose
     * {@code merchantMatch} is contained in {@code merchantName}, case-insensitive.
     */
    public Optional<Rule> firstMatch(final String merchantName, final Collection<Rule> rules) {
        if (!StringUtils.hasText(merchantName) || rules == null) {
            return Optional.empty();
        }
        final String haystack = merchantName.toLowerCase(Locale.ROOT);
        return rules.stream()
                .filter(Rule::isEnabled)
                .filter(rule -> StringUtils.hasText(rule.getMerchantMatch()))
                .sorted(Comparator.comparingInt(Rule::getPriority))
                .filter(rule -> haystack.contains(rule.getMerchantMatch().toLowerCase(Locale.ROOT)))
                .findFirst();
    }

    /**
     * Applies a matched rule's actions to a transaction and records the match on
     * the rule. Sets the category, links the merchant when the rule specifies
     * one, and sets {@code needsReview} from the rule's auto-approve toggle.
     */
    public void apply(final Transaction transaction, final Rule rule) {
        if (rule.getCategory() != null) {
            transaction.setCategory(rule.getCategory());
        }
        if (rule.getMerchant() != null) {
            transaction.setMerchant(rule.getMerchant());
        }
        if (rule.isShiftToNextMonth() && !alreadyShifted(transaction)) {
            // Month-end charges that belong to the next month's budget (e.g.
            // daycare): move to the 1st of the following month. The
            // source-reported date stays pinned for dedup, and the guard makes
            // re-applying the rule a no-op instead of drifting further.
            final LocalDate day = transaction.getPostedAt().atZone(ZoneOffset.UTC).toLocalDate();
            // Noon UTC: a timezone-safe calendar day (renders identically everywhere).
            transaction.setPostedAt(day.plusMonths(1).withDayOfMonth(1).atTime(12, 0).atOffset(ZoneOffset.UTC).toInstant());
        }
        transaction.setNeedsReview(!rule.isAutoApprove());
        rule.setMatchCount(rule.getMatchCount() + 1);
        rule.setLastMatchedAt(Instant.now());
    }

    private boolean alreadyShifted(final Transaction transaction) {
        return transaction.getSourcePostedAt() != null
                && !transaction.getSourcePostedAt().equals(transaction.getPostedAt());
    }

    /**
     * Categorizes one transaction against the given rules. On a match, applies
     * the rule and returns it; with no match the transaction stays uncategorized
     * and flagged for review.
     */
    public Optional<Rule> categorize(final Transaction transaction, final Collection<Rule> rules) {
        final Optional<Rule> match = firstMatch(transaction.getMerchantName(), rules);
        if (match.isPresent()) {
            apply(transaction, match.get());
        } else {
            transaction.setNeedsReview(true);
        }
        return match;
    }

}
