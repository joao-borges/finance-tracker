package ca.joaoborges.finance.budget;

import java.math.BigDecimal;

/**
 * One planned amount to set for a category in a month. A null/zero amount clears
 * the budget for that category.
 */
public record BudgetEntry(Long categoryId, BigDecimal plannedAmount) {
}
