package ca.joaoborges.finance.budget;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;

/**
 * Everything the Budgets page needs for a month: an income section and expense
 * groups, each line carrying planned / actual / remaining, plus the rollups.
 * Actual spend reads positive (outflow negated); income actual is the inflow.
 */
@Builder
public record BudgetSummary(
        String month,
        BigDecimal plannedIncome,
        BigDecimal actualIncome,
        BigDecimal plannedExpense,
        BigDecimal actualExpense,
        BigDecimal leftToBudget,
        List<BudgetLine> income,
        List<BudgetGroup> groups) {

    @Builder
    public record BudgetGroup(Long groupId, String groupName, boolean collapsed, BigDecimal planned, BigDecimal actual, List<BudgetLine> categories) {
    }

    @Builder
    public record BudgetLine(Long categoryId, String name, String icon, BigDecimal planned, BigDecimal actual, BigDecimal remaining) {
    }

}
