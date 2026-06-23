package ca.joaoborges.finance.dashboard;

import ca.joaoborges.finance.account.AccountType;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;

/**
 * Everything the landing page needs in one call: grouped account balances, the
 * review-queue size, and current-month budget violations.
 */
@Builder
public record DashboardSummary(
        String month,
        BigDecimal netWorth,
        long pendingReviewCount,
        List<AccountGroup> accountGroups,
        List<BudgetAlert> budgetAlerts) {

    @Builder
    public record AccountGroup(String label, BigDecimal total, List<AccountSummary> accounts) {
    }

    @Builder
    public record AccountSummary(Long id, String name, AccountType type, BigDecimal balance, String currency, String logoUrl) {
    }

    @Builder
    public record BudgetAlert(Long categoryId, String categoryName, BigDecimal planned, BigDecimal spent, int percent, int level) {
    }

}
