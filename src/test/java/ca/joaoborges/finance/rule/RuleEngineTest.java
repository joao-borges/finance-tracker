package ca.joaoborges.finance.rule;

import ca.joaoborges.finance.category.Category;
import ca.joaoborges.finance.merchant.Merchant;
import ca.joaoborges.finance.transaction.Transaction;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleEngineTest {

    private final RuleEngine engine = new RuleEngine();

    private Category category(final String name) {
        return Category.builder().name(name).build();
    }

    private Rule rule(final String match, final int priority, final boolean enabled,
                      final boolean autoApprove, final Category category) {
        return Rule.builder()
                .name(match)
                .merchantMatch(match)
                .priority(priority)
                .enabled(enabled)
                .autoApprove(autoApprove)
                .category(category)
                .build();
    }

    private Transaction transaction(final String merchantName) {
        return Transaction.builder()
                .merchantName(merchantName)
                .amount(new BigDecimal("-10.00"))
                .build();
    }

    @Test
    void matchesCaseInsensitiveSubstring() {
        final Rule tim = rule("tim hortons", 0, true, false, category("Dining"));

        final Optional<Rule> match = engine.firstMatch("TIM HORTONS #4521", List.of(tim));

        assertTrue(match.isPresent());
        assertSame(tim, match.get());
    }

    @Test
    void firstMatchWinsByPriorityRegardlessOfInputOrder() {
        final Rule generic = rule("amzn", 10, true, false, category("Shopping"));
        final Rule specific = rule("amzn mktp", 1, true, false, category("Household"));

        // Pass in the "wrong" order; the engine must still pick the lower priority.
        final Optional<Rule> match = engine.firstMatch("AMZN MKTP CA*1A2B3", List.of(generic, specific));

        assertTrue(match.isPresent());
        assertSame(specific, match.get());
    }

    @Test
    void skipsDisabledRules() {
        final Rule disabled = rule("netflix", 0, false, true, category("Subscriptions"));

        assertTrue(engine.firstMatch("NETFLIX.COM", List.of(disabled)).isEmpty());
    }

    @Test
    void skipsBlankMerchantMatch() {
        final Rule blank = Rule.builder()
                .name("blank")
                .merchantMatch("   ")
                .enabled(true)
                .category(category("Misc"))
                .build();

        assertTrue(engine.firstMatch("ANYTHING", List.of(blank)).isEmpty());
    }

    @Test
    void noMatchReturnsEmpty() {
        final Rule tim = rule("tim hortons", 0, true, false, category("Dining"));

        assertTrue(engine.firstMatch("STARBUCKS #99", List.of(tim)).isEmpty());
    }

    @Test
    void categorizeAutoApproveSetsCategoryAndClearsReview() {
        final Category dining = category("Dining");
        final Merchant tims = Merchant.builder().name("Tim Hortons").build();
        final Rule tim = Rule.builder()
                .name("tim").merchantMatch("tim hortons").priority(0)
                .enabled(true).autoApprove(true).category(dining).merchant(tims)
                .build();
        final Transaction transaction = transaction("TIM HORTONS #4521");

        final Optional<Rule> match = engine.categorize(transaction, List.of(tim));

        assertTrue(match.isPresent());
        assertSame(dining, transaction.getCategory());
        assertSame(tims, transaction.getMerchant());
        assertFalse(transaction.isNeedsReview());
    }

    @Test
    void categorizeWithoutAutoApproveLeavesReviewSet() {
        final Category dining = category("Dining");
        final Rule tim = rule("tim hortons", 0, true, false, dining);
        final Transaction transaction = transaction("TIM HORTONS #4521");

        engine.categorize(transaction, List.of(tim));

        assertSame(dining, transaction.getCategory());
        assertTrue(transaction.isNeedsReview());
    }

    @Test
    void categorizeNoMatchStaysUncategorizedAndNeedsReview() {
        final Rule tim = rule("tim hortons", 0, true, false, category("Dining"));
        final Transaction transaction = transaction("STARBUCKS #99");

        final Optional<Rule> match = engine.categorize(transaction, List.of(tim));

        assertTrue(match.isEmpty());
        assertNull(transaction.getCategory());
        assertNull(transaction.getMerchant());
        assertTrue(transaction.isNeedsReview());
    }

    @Test
    void applyRecordsTheMatchOnTheRule() {
        final Rule tim = rule("tim hortons", 0, true, true, category("Dining"));
        final Transaction transaction = transaction("TIM HORTONS #4521");

        engine.apply(transaction, tim);

        assertEquals(1, tim.getMatchCount());
        assertTrue(tim.getLastMatchedAt() != null);
    }

    @Test
    void mergeDoesNotLinkMerchantWhenRuleHasNone() {
        final Rule tim = rule("tim hortons", 0, true, true, category("Dining"));
        final Transaction transaction = transaction("TIM HORTONS #4521");

        engine.categorize(transaction, List.of(tim));

        assertNull(transaction.getMerchant());
    }

}
