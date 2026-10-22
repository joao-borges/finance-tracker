package ca.joaoborges.finance.amazon;

import ca.joaoborges.finance.amazon.AmazonChargeMatcher.Candidate;
import ca.joaoborges.finance.amazon.AmazonChargeMatcher.ChargeKey;
import ca.joaoborges.finance.amazon.AmazonChargeMatcher.PendingCharge;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AmazonChargeMatcherTest {

    private static final LocalDate SEP_5 = LocalDate.of(2026, 9, 5);

    private static ChargeKey key(final LocalDate day, final String amount, final String last4) {
        return new ChargeKey(day, new BigDecimal(amount), last4);
    }

    private static Candidate outflow(final long id, final LocalDate day, final String amount, final String account) {
        return new Candidate(id, day, new BigDecimal(amount).negate(), account);
    }

    @Test
    void matchesAnExactOutflowPostedAFewDaysAfterTheCharge() {
        final Map<Long, Long> result = AmazonChargeMatcher.match(
                List.of(new PendingCharge(1, key(SEP_5, "45.67", "1004"))),
                List.of(outflow(10, SEP_5.plusDays(2), "45.67", "Amex Cobalt")),
                Map.of());
        assertEquals(Map.of(1L, 10L), result);
    }

    @Test
    void ignoresWrongAmountsAndRowsOutsideTheWindow() {
        final Map<Long, Long> result = AmazonChargeMatcher.match(
                List.of(new PendingCharge(1, key(SEP_5, "45.67", null))),
                List.of(outflow(10, SEP_5, "45.68", "Visa"),
                        outflow(11, SEP_5.plusDays(AmazonChargeMatcher.DAYS_AFTER + 1), "45.67", "Visa"),
                        outflow(12, SEP_5.minusDays(AmazonChargeMatcher.DAYS_BEFORE + 1), "45.67", "Visa"),
                        new Candidate(13, SEP_5, new BigDecimal("45.67"), "Visa")),
                Map.of());
        assertTrue(result.isEmpty());
    }

    @Test
    void comparesAmountsByValueNotScale() {
        final Map<Long, Long> result = AmazonChargeMatcher.match(
                List.of(new PendingCharge(1, new ChargeKey(SEP_5, new BigDecimal("12.5000"), null))),
                List.of(outflow(10, SEP_5, "12.50", "Visa")),
                Map.of());
        assertEquals(Map.of(1L, 10L), result);
    }

    /** Two ABC-Fitness-style identical charges the same day: each takes its own row. */
    @Test
    void identicalSameDayChargesForTwoOrdersSpreadOverTwoTransactions() {
        final Map<Long, Long> result = AmazonChargeMatcher.match(
                List.of(new PendingCharge(1, key(SEP_5, "31.49", null)),
                        new PendingCharge(2, key(SEP_5, "31.49", null))),
                List.of(outflow(10, SEP_5, "31.49", "Visa"), outflow(11, SEP_5, "31.49", "Visa")),
                Map.of());
        assertEquals(Map.of(1L, 10L, 2L, 11L), result);
    }

    /** One card charge covering two orders: both invoices list it, both link the one row. */
    @Test
    void oneChargeListedOnTwoInvoicesLinksBothToTheSameTransaction() {
        final Map<Long, Long> result = AmazonChargeMatcher.match(
                List.of(new PendingCharge(1, key(SEP_5, "80.00", "1004")),
                        new PendingCharge(2, key(SEP_5, "80.00", "1004"))),
                List.of(outflow(10, SEP_5.plusDays(1), "80.00", "Amex 1004")),
                Map.of());
        assertEquals(Map.of(1L, 10L, 2L, 10L), result);
    }

    @Test
    void neverReusesATransactionClaimedByADifferentCharge() {
        final Map<Long, Long> result = AmazonChargeMatcher.match(
                List.of(new PendingCharge(2, key(SEP_5.plusDays(1), "20.00", null))),
                List.of(outflow(10, SEP_5.plusDays(1), "20.00", "Visa")),
                Map.of(10L, Set.of(key(SEP_5, "20.00", null))));
        assertTrue(result.isEmpty());
    }

    @Test
    void prefersTheAccountCarryingTheCardDigitsThenTheNearestDate() {
        final Map<Long, Long> byCard = AmazonChargeMatcher.match(
                List.of(new PendingCharge(1, key(SEP_5, "15.00", "1004"))),
                List.of(outflow(10, SEP_5, "15.00", "RBC Visa 5512"),
                        outflow(11, SEP_5.plusDays(3), "15.00", "Amex 1004")),
                Map.of());
        assertEquals(Map.of(1L, 11L), byCard);

        final Map<Long, Long> byDate = AmazonChargeMatcher.match(
                List.of(new PendingCharge(1, key(SEP_5, "15.00", null))),
                List.of(outflow(10, SEP_5.plusDays(4), "15.00", "Visa"),
                        outflow(11, SEP_5.plusDays(1), "15.00", "Visa")),
                Map.of());
        assertEquals(Map.of(1L, 11L), byDate);
    }

    @Test
    void recognisesAmazonDescriptorsFromEitherFeed() {
        assertTrue(AmazonChargeMatcher.isAmazon("Amazon"));
        assertTrue(AmazonChargeMatcher.isAmazon("AMZN MKTP CA*580XN60E3 866-216-1072"));
        assertTrue(AmazonChargeMatcher.isAmazon("SQ *SOMETHING", "Amazon.ca"));
        assertFalse(AmazonChargeMatcher.isAmazon("COSTCO GAS", null));
    }

}
