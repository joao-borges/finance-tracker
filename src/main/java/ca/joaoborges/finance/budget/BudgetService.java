package ca.joaoborges.finance.budget;

import ca.joaoborges.finance.category.Category;
import ca.joaoborges.finance.category.CategoryRepository;
import ca.joaoborges.finance.transaction.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Budget math: planned (from {@link Budget} rows) vs actual (from the budget
 * transaction predicate) per category, grouped, for a month. See the Budgets
 * section of {@code DESIGN.md}.
 */
@Service
@RequiredArgsConstructor
public class BudgetService {

    /** Where a pulled-forward shortfall lands. Created on demand if missing. */
    private static final String ADJUSTMENT_CATEGORY = "Budget Adjustment";
    private static final String ADJUSTMENT_GROUP = "Financial";

    private final BudgetRepository budgetRepository;
    private final CategoryRepository categoryRepository;
    private final ca.joaoborges.finance.category.CategoryGroupRepository categoryGroupRepository;
    private final TransactionRepository transactionRepository;

    private record GroupAccumulator(String name, boolean collapsed, List<BudgetSummary.BudgetLine> lines) {
    }

    @Transactional(readOnly = true)
    public BudgetSummary summary(final String month) {
        return summary(month, false);
    }

    @Transactional(readOnly = true)
    public BudgetSummary summary(final String month, final boolean includeHidden) {
        final YearMonth ym = parseMonth(month);
        final Instant start = ym.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        final Instant end = ym.plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        final Map<Long, BigDecimal> planned = new java.util.HashMap<>();
        budgetRepository.findByMonth(month).forEach(b -> planned.put(b.getCategory().getId(), b.getPlannedAmount()));

        final List<BudgetSummary.BudgetLine> income = new ArrayList<>();
        final Map<Long, GroupAccumulator> expenseGroups = new LinkedHashMap<>();
        BigDecimal plannedIncome = BigDecimal.ZERO;
        BigDecimal actualIncome = BigDecimal.ZERO;
        BigDecimal plannedExpense = BigDecimal.ZERO;
        BigDecimal actualExpense = BigDecimal.ZERO;

        for (final Category category : categoryRepository.findAllByOrderBySortOrderAscNameAsc()) {
            if (category.isArchived() || (category.isHidden() && !includeHidden)) {
                continue;
            }
            // A one-time category belongs to a single month and must not clutter
            // every other month's budget.
            if (category.getOneTimeMonth() != null && !category.getOneTimeMonth().equals(month)) {
                continue;
            }
            final BigDecimal plan = planned.getOrDefault(category.getId(), BigDecimal.ZERO);
            final BigDecimal sum = transactionRepository.sumForCategoryInMonth(category.getId(), start, end);

            if (category.isIncome()) {
                final BigDecimal actual = sum;
                income.add(line(category, plan, actual));
                plannedIncome = plannedIncome.add(plan);
                actualIncome = actualIncome.add(actual);
            } else {
                final BigDecimal actual = sum.negate();
                final Long groupId = category.getGroup() == null ? null : category.getGroup().getId();
                final String groupName = category.getGroup() == null ? "Ungrouped" : category.getGroup().getName();
                final boolean collapsed = category.getGroup() != null && category.getGroup().isCollapsed();
                expenseGroups.computeIfAbsent(groupId, key -> new GroupAccumulator(groupName, collapsed, new ArrayList<>()))
                        .lines().add(line(category, plan, actual));
                plannedExpense = plannedExpense.add(plan);
                actualExpense = actualExpense.add(actual);
            }
        }

        final List<BudgetSummary.BudgetGroup> groups = new ArrayList<>();
        expenseGroups.forEach((groupId, acc) -> groups.add(BudgetSummary.BudgetGroup.builder()
                .groupId(groupId)
                .groupName(acc.name())
                .collapsed(acc.collapsed())
                .planned(acc.lines().stream().map(BudgetSummary.BudgetLine::planned).reduce(BigDecimal.ZERO, BigDecimal::add))
                .actual(acc.lines().stream().map(BudgetSummary.BudgetLine::actual).reduce(BigDecimal.ZERO, BigDecimal::add))
                .categories(acc.lines())
                .build()));

        return BudgetSummary.builder()
                .month(month)
                .plannedIncome(plannedIncome)
                .actualIncome(actualIncome)
                .plannedExpense(plannedExpense)
                .actualExpense(actualExpense)
                .leftToBudget(plannedIncome.subtract(plannedExpense))
                .income(income)
                .groups(groups)
                .build();
    }

    @Transactional
    public BudgetSummary setBudgets(final String month, final List<BudgetEntry> entries, final boolean includeHidden) {
        requireEditable(month);
        final Map<Long, Budget> existing = new java.util.HashMap<>();
        budgetRepository.findByMonth(month).forEach(b -> existing.put(b.getCategory().getId(), b));

        for (final BudgetEntry entry : entries) {
            if (entry.categoryId() == null) {
                continue;
            }
            final Budget current = existing.get(entry.categoryId());
            final BigDecimal amount = entry.plannedAmount();
            if (amount == null || amount.compareTo(BigDecimal.ZERO) == 0) {
                if (current != null) {
                    budgetRepository.delete(current);
                }
                continue;
            }
            final Category category = current != null ? current.getCategory()
                    : categoryRepository.findById(entry.categoryId())
                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown category " + entry.categoryId()));
            // Giving a category a planned amount auto-unhides it on the budget page.
            if (category.isHidden()) {
                category.setHidden(false);
                categoryRepository.save(category);
            }
            if (current != null) {
                current.setPlannedAmount(amount);
                budgetRepository.save(current);
            } else {
                budgetRepository.save(Budget.builder().month(month).category(category).plannedAmount(amount).build());
            }
        }
        return summary(month, includeHidden);
    }

    /** Remove all planned amounts for the month. */
    @Transactional
    public BudgetSummary clear(final String month, final boolean includeHidden) {
        requireEditable(month);
        budgetRepository.deleteAll(budgetRepository.findByMonth(month));
        return summary(month, includeHidden);
    }

    /** Replace this month's planned amounts with the previous month's. */
    @Transactional
    public BudgetSummary copyFromPrevious(final String month, final boolean includeHidden) {
        final YearMonth ym = requireEditable(month);
        final List<Budget> sources = budgetRepository.findByMonth(ym.minusMonths(1).toString());
        budgetRepository.deleteAll(budgetRepository.findByMonth(month));
        budgetRepository.flush();
        for (final Budget source : sources) {
            final Category category = source.getCategory();
            if (category.isHidden()) {
                category.setHidden(false);
                categoryRepository.save(category);
            }
            budgetRepository.save(Budget.builder()
                    .month(month)
                    .category(category)
                    .plannedAmount(source.getPlannedAmount())
                    .build());
        }
        return summary(month, includeHidden);
    }

    /**
     * Carry the previous month's planned shortfall into this month as a planned
     * expense on {@value #ADJUSTMENT_CATEGORY}.
     *
     * <p>The shortfall is the previous month's <em>plan</em> — planned income
     * minus planned expenses — not what was actually spent, so it is stable the
     * moment that month's budget is set and doesn't drift as transactions land.
     * A month that planned to break even or better pulls nothing. The amount is
     * SET, not added, so pressing the button twice is a no-op rather than
     * doubling the adjustment.
     */
    @Transactional
    public BudgetSummary pullPreviousShortfall(final String month, final boolean includeHidden) {
        final YearMonth ym = requireEditable(month);
        final String previous = ym.minusMonths(1).toString();

        BigDecimal plannedIncome = BigDecimal.ZERO;
        BigDecimal plannedExpense = BigDecimal.ZERO;
        for (final Budget budget : budgetRepository.findByMonth(previous)) {
            final BigDecimal planned = budget.getPlannedAmount() == null ? BigDecimal.ZERO : budget.getPlannedAmount();
            if (budget.getCategory().isIncome()) {
                plannedIncome = plannedIncome.add(planned);
            } else {
                plannedExpense = plannedExpense.add(planned);
            }
        }
        final BigDecimal shortfall = plannedIncome.subtract(plannedExpense);
        final Category adjustment = adjustmentCategory();
        if (shortfall.signum() >= 0) {
            // The previous month planned to break even or better. Clear any
            // adjustment a previous press left behind, so the line always
            // reflects the previous month as it stands now.
            budgetRepository.findByMonthAndCategoryId(month, adjustment.getId())
                    .ifPresent(budgetRepository::delete);
            return summary(month, includeHidden);
        }

        final BigDecimal amount = shortfall.abs();
        budgetRepository.findByMonthAndCategoryId(month, adjustment.getId())
                .ifPresentOrElse(existing -> {
                    existing.setPlannedAmount(amount);
                    budgetRepository.save(existing);
                }, () -> budgetRepository.save(Budget.builder()
                        .month(month)
                        .category(adjustment)
                        .plannedAmount(amount)
                        .build()));
        return summary(month, includeHidden);
    }

    /**
     * Create a category scoped to one month and plan an amount against it. Used
     * for genuine one-offs (a single payment to a friend) that deserve their own
     * budget line without permanently enlarging the category list.
     */
    @Transactional
    public BudgetSummary addOneTimeCategory(final String month,
                                            final BudgetController.OneTimeCategoryRequest request,
                                            final boolean includeHidden) {
        requireEditable(month);
        if (request == null || !org.springframework.util.StringUtils.hasText(request.name())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A name is required");
        }
        if (request.plannedAmount() == null || request.plannedAmount().signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A planned amount greater than zero is required");
        }
        final ca.joaoborges.finance.category.CategoryGroup group = request.groupId() == null ? null
                : categoryGroupRepository.findById(request.groupId())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown group " + request.groupId()));
        final Category category = categoryRepository.save(Category.builder()
                .group(group)
                .name(request.name().trim())
                .icon(request.icon())
                .oneTimeMonth(month)
                .build());
        budgetRepository.save(Budget.builder()
                .month(month)
                .category(category)
                .plannedAmount(request.plannedAmount())
                .build());
        return summary(month, includeHidden);
    }

    private Category adjustmentCategory() {
        final ca.joaoborges.finance.category.CategoryGroup group =
                categoryGroupRepository.findByName(ADJUSTMENT_GROUP)
                        .orElseGet(() -> categoryGroupRepository.save(
                                ca.joaoborges.finance.category.CategoryGroup.builder().name(ADJUSTMENT_GROUP).build()));
        final Category category = categoryRepository.findByGroupAndName(group, ADJUSTMENT_CATEGORY)
                .orElseGet(() -> categoryRepository.save(Category.builder()
                        .group(group)
                        .name(ADJUSTMENT_CATEGORY)
                        .icon("\u2696\uFE0F")
                        .build()));
        // A carried shortfall must be visible on the page that just created it.
        if (category.isHidden()) {
            category.setHidden(false);
            categoryRepository.save(category);
        }
        return category;
    }

    private BudgetSummary.BudgetLine line(final Category category, final BigDecimal planned, final BigDecimal actual) {
        return BudgetSummary.BudgetLine.builder()
                .categoryId(category.getId())
                .name(category.getName())
                .icon(category.getIcon())
                .hidden(category.isHidden())
                .planned(planned)
                .actual(actual)
                .remaining(planned.subtract(actual))
                .build();
    }

    /** Past months are read-only; only the current month and future are editable. */
    private YearMonth requireEditable(final String month) {
        final YearMonth ym = parseMonth(month);
        if (ym.isBefore(YearMonth.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Past months are read-only");
        }
        return ym;
    }

    private YearMonth parseMonth(final String month) {
        try {
            return YearMonth.parse(month);
        } catch (final DateTimeParseException invalid) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Month must be YYYY-MM");
        }
    }

}
