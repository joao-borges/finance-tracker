package ca.joaoborges.finance.ingest;

import ca.joaoborges.finance.account.Account;
import ca.joaoborges.finance.account.AccountRepository;
import ca.joaoborges.finance.account.AccountType;
import ca.joaoborges.finance.budget.BudgetAlertService;
import ca.joaoborges.finance.category.Category;
import ca.joaoborges.finance.common.ContentHashing;
import ca.joaoborges.finance.common.ImportCutoff;
import ca.joaoborges.finance.common.SourceType;
import ca.joaoborges.finance.csv.ParsedTransaction;
import ca.joaoborges.finance.rule.Rule;
import ca.joaoborges.finance.rule.RuleEngine;
import ca.joaoborges.finance.rule.RuleRepository;
import ca.joaoborges.finance.transaction.Transaction;
import ca.joaoborges.finance.transaction.TransactionRepository;
import ca.joaoborges.finance.webhook.DiscordNotifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Shared ingest pipeline: turns parsed rows (from any CSV format) into
 * transactions — find-or-create the account, run the rules engine, persist, and
 * record an {@link ImportRun}. Source-specific parsing lives in the csv package;
 * everything downstream is identical regardless of source.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IngestService {

    private static final String DEFAULT_CURRENCY = "CAD";

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final RuleRepository ruleRepository;
    private final ImportRunRepository importRunRepository;
    private final RuleEngine ruleEngine;
    private final DiscordNotifier discordNotifier;
    private final BudgetAlertService budgetAlertService;
    private final ImportCutoff importCutoff;

    /** Per category+month spend added by this import, for budget/threshold crossing checks. */
    private record AlertKey(Long categoryId, YearMonth month) {
    }

    @Transactional
    public ImportRun ingest(final List<ParsedTransaction> rows, final SourceType source, final String fileName) {
        final ImportRun run = importRunRepository.save(ImportRun.builder()
                .source(source)
                .status(ImportStatus.RUNNING)
                .startedAt(Instant.now())
                .fileName(fileName)
                .build());

        final List<Rule> enabledRules = ruleRepository.findByEnabledTrueOrderByPriorityAscIdAsc();
        final Instant fallbackPostedAt = Instant.now();
        final Map<String, Integer> byAccount = new LinkedHashMap<>();
        final Map<AlertKey, BigDecimal> spendByCategoryMonth = new HashMap<>();
        final Map<Long, Category> categoriesById = new HashMap<>();
        int newCount = 0;
        int reviewed = 0;
        int needsReview = 0;
        int skippedBeforeCutoff = 0;

        for (final ParsedTransaction row : rows) {
            final Instant postedAt = row.postedAt() != null ? row.postedAt() : fallbackPostedAt;
            if (importCutoff.excludes(postedAt)) {
                skippedBeforeCutoff++;
                continue;
            }
            final Account account = resolveAccount(row.accountName());
            byAccount.merge(account.getName(), 1, Integer::sum);

            final String hash = ContentHashing.of(account.getName(), row.amount(), row.merchantName());
            final Transaction transaction = Transaction.builder()
                    .account(account)
                    .source(source)
                    .merchantName(row.merchantName())
                    .amount(row.amount())
                    .postedAt(postedAt)
                    .currency(account.getCurrency())
                    .contentHash(hash)
                    .dedupKey(hash)
                    .needsReview(true)
                    .importRun(run)
                    .build();

            ruleEngine.categorize(transaction, enabledRules);
            transactionRepository.save(transaction);
            newCount++;
            if (transaction.isNeedsReview()) {
                needsReview++;
            } else {
                reviewed++;
            }

            final Category category = transaction.getCategory();
            if (category != null && !category.isIncome()) {
                final AlertKey key = new AlertKey(category.getId(), YearMonth.from(postedAt.atZone(ZoneOffset.UTC)));
                spendByCategoryMonth.merge(key, transaction.getAmount().negate(), BigDecimal::add);
                categoriesById.putIfAbsent(category.getId(), category);
            }
        }

        if (skippedBeforeCutoff > 0) {
            log.info("Import '{}': skipped {} row(s) posted before the import cutoff", fileName, skippedBeforeCutoff);
        }

        run.setNewCount(newCount);
        run.setAccountCount(byAccount.size());
        run.setStatus(ImportStatus.SUCCESS);
        run.setFinishedAt(Instant.now());
        final ImportRun saved = importRunRepository.save(run);

        discordNotifier.sendImportSummary(new ImportSummary(fileName, newCount, reviewed, needsReview, byAccount));
        spendByCategoryMonth.forEach((key, delta) ->
                budgetAlertService.checkAfterSpend(categoriesById.get(key.categoryId()), key.month(), delta));
        return saved;
    }

    /**
     * Finds (or creates) the account for an import key, following a merge link so
     * transactions always attach to the canonical account.
     */
    private Account resolveAccount(final String importRef) {
        final Account account = accountRepository.findByImportRef(importRef)
                .orElseGet(() -> accountRepository.save(Account.builder()
                        .name(importRef)
                        .importRef(importRef)
                        .type(guessType(importRef))
                        .currency(DEFAULT_CURRENCY)
                        .build()));
        return account.getMergedInto() != null ? account.getMergedInto() : account;
    }

    private AccountType guessType(final String name) {
        final String lower = name.toLowerCase(Locale.ROOT);
        if (lower.contains("visa") || lower.contains("mastercard") || lower.contains("amex") || lower.contains("credit")) {
            return AccountType.CREDIT_CARD;
        }
        if (lower.contains("saving")) {
            return AccountType.SAVINGS;
        }
        if (lower.contains("loan")) {
            return AccountType.LOAN;
        }
        return AccountType.CHECKING;
    }

}
