package ca.joaoborges.finance.budget;

import ca.joaoborges.finance.category.Category;
import ca.joaoborges.finance.transaction.TransactionRepository;
import ca.joaoborges.finance.webhook.DiscordNotifier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;

/**
 * Fires Discord alerts when categorized spend crosses a limit for a category in
 * a month: over the month's budget (red) or over the category's configured
 * threshold (yellow). Alerts only on the crossing — given the spend just applied
 * ({@code spentDelta}), it compares before vs after so it won't re-alert a
 * category that was already over.
 */
@Service
@RequiredArgsConstructor
public class BudgetAlertService {

    private final BudgetRepository budgetRepository;
    private final TransactionRepository transactionRepository;
    private final DiscordNotifier discordNotifier;

    public void checkAfterSpend(final Category category, final YearMonth month, final BigDecimal spentDelta) {
        if (category == null || category.isIncome() || spentDelta == null || spentDelta.signum() <= 0) {
            return;
        }
        final Instant start = month.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        final Instant end = month.plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        final BigDecimal spentAfter = transactionRepository.sumForCategoryInMonth(category.getId(), start, end).negate();
        final BigDecimal spentBefore = spentAfter.subtract(spentDelta);
        final String label = month.toString();

        final BigDecimal planned = budgetRepository.findByMonthAndCategoryId(label, category.getId())
                .map(Budget::getPlannedAmount)
                .orElse(null);
        if (planned != null && planned.signum() > 0 && crossesAbove(spentBefore, spentAfter, planned)) {
            discordNotifier.sendBudgetOverflow(category.getName(), label, spentAfter, planned);
        }

        final BigDecimal threshold = category.getAlertThreshold();
        if (threshold != null && threshold.signum() > 0 && reaches(spentBefore, spentAfter, threshold)) {
            discordNotifier.sendThresholdAlert(category.getName(), label, spentAfter, threshold);
        }
    }

    /** Over budget means STRICTLY over — landing exactly on the limit is on-budget, not an overflow. */
    private boolean crossesAbove(final BigDecimal before, final BigDecimal after, final BigDecimal limit) {
        return before.compareTo(limit) <= 0 && after.compareTo(limit) > 0;
    }

    /** A threshold is a warning level — hitting it exactly counts as reached. */
    private boolean reaches(final BigDecimal before, final BigDecimal after, final BigDecimal limit) {
        return before.compareTo(limit) < 0 && after.compareTo(limit) >= 0;
    }

}
