package ca.joaoborges.finance.dashboard;

import ca.joaoborges.finance.account.Account;
import ca.joaoborges.finance.account.AccountRepository;
import ca.joaoborges.finance.account.AccountType;
import ca.joaoborges.finance.budget.Budget;
import ca.joaoborges.finance.budget.BudgetRepository;
import ca.joaoborges.finance.transaction.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final Set<AccountType> CASH = EnumSet.of(AccountType.CHECKING, AccountType.SAVINGS, AccountType.CASH);

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final BudgetRepository budgetRepository;

    @Transactional(readOnly = true)
    public DashboardSummary summary() {
        final List<Account> visible = accountRepository.findAll().stream()
                .filter(account -> !account.isHidden() && !account.isArchived())
                .toList();

        final List<DashboardSummary.AccountGroup> groups = List.of(
                group("Cash", visible, type -> CASH.contains(type)),
                group("Credit Cards", visible, type -> type == AccountType.CREDIT_CARD),
                group("Loans", visible, type -> type == AccountType.LOAN));

        final BigDecimal netWorth = visible.stream()
                .map(account -> account.getBalance() == null ? BigDecimal.ZERO : account.getBalance())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return DashboardSummary.builder()
                .month(YearMonth.now(ZoneOffset.UTC).toString())
                .netWorth(netWorth)
                .pendingReviewCount(transactionRepository.countByNeedsReviewTrueAndSplitFalseAndDedupFalse())
                .accountGroups(groups)
                .budgetAlerts(budgetAlerts())
                .build();
    }

    private DashboardSummary.AccountGroup group(final String label, final List<Account> accounts,
                                                final java.util.function.Predicate<AccountType> matches) {
        final List<DashboardSummary.AccountSummary> members = accounts.stream()
                .filter(account -> matches.test(account.getType()))
                .map(account -> DashboardSummary.AccountSummary.builder()
                        .id(account.getId())
                        .name(account.getName())
                        .type(account.getType())
                        .balance(account.getBalance())
                        .currency(account.getCurrency())
                        .logoUrl(account.getLogoUrl())
                        .build())
                .toList();
        final BigDecimal total = members.stream()
                .map(member -> member.balance() == null ? BigDecimal.ZERO : member.balance())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return DashboardSummary.AccountGroup.builder().label(label).total(total).accounts(members).build();
    }

    private List<DashboardSummary.BudgetAlert> budgetAlerts() {
        final YearMonth month = YearMonth.now(ZoneOffset.UTC);
        final Instant start = month.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        final Instant end = month.plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        final List<DashboardSummary.BudgetAlert> alerts = new ArrayList<>();
        for (final Budget budget : budgetRepository.findByMonth(month.toString())) {
            final BigDecimal planned = budget.getPlannedAmount();
            if (planned == null || planned.signum() <= 0) {
                continue;
            }
            // Expenses are stored negative; negate so spend reads positive.
            final BigDecimal spent = transactionRepository
                    .sumForCategoryInMonth(budget.getCategory().getId(), start, end)
                    .negate();
            final BigDecimal threshold = budget.getCategory().getAlertThreshold();
            // Red only when actually over budget; yellow only when a configured
            // threshold is reached. At/under 100% with no threshold = no alert.
            final boolean over = spent.compareTo(planned) > 0;
            final boolean thresholdHit = threshold != null && threshold.signum() > 0 && spent.compareTo(threshold) >= 0;
            if (!over && !thresholdHit) {
                continue;
            }
            final int percent = spent.multiply(BigDecimal.valueOf(100))
                    .divide(planned, 0, RoundingMode.HALF_UP)
                    .intValue();
            alerts.add(DashboardSummary.BudgetAlert.builder()
                    .categoryId(budget.getCategory().getId())
                    .categoryName(budget.getCategory().getName())
                    .planned(planned)
                    .spent(spent)
                    .percent(percent)
                    .level(over ? 120 : 80)
                    .build());
        }
        return alerts;
    }

}
