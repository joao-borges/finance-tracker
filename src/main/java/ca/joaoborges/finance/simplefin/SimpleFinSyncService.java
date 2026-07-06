package ca.joaoborges.finance.simplefin;

import ca.joaoborges.finance.account.Account;
import ca.joaoborges.finance.account.AccountRepository;
import ca.joaoborges.finance.account.AccountType;
import ca.joaoborges.finance.budget.BudgetAlertService;
import ca.joaoborges.finance.category.Category;
import ca.joaoborges.finance.common.ContentHashing;
import ca.joaoborges.finance.common.SourceType;
import ca.joaoborges.finance.ingest.ImportRun;
import ca.joaoborges.finance.ingest.ImportRunRepository;
import ca.joaoborges.finance.ingest.ImportStatus;
import ca.joaoborges.finance.ingest.ImportSummary;
import ca.joaoborges.finance.rule.Rule;
import ca.joaoborges.finance.rule.RuleEngine;
import ca.joaoborges.finance.rule.RuleRepository;
import ca.joaoborges.finance.transaction.Transaction;
import ca.joaoborges.finance.transaction.TransactionRepository;
import ca.joaoborges.finance.webhook.DiscordNotifier;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * SimpleFIN sync: pull accounts/balances/posted transactions and run them
 * through the same downstream pipeline as CSV (rules → review, budget alerts,
 * import summary). Accounts upsert by {@code simplefin_id}; transactions dedup
 * by the provider id (posted-only). The access URL is a secret kept in the DB.
 */
@Service
@RequiredArgsConstructor
public class SimpleFinSyncService {

    private final SimpleFinConnectionRepository connectionRepository;
    private final SimpleFinClient simpleFinClient;
    private final ObjectMapper objectMapper;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final RuleRepository ruleRepository;
    private final ImportRunRepository importRunRepository;
    private final RuleEngine ruleEngine;
    private final DiscordNotifier discordNotifier;
    private final BudgetAlertService budgetAlertService;
    private final ca.joaoborges.finance.match.MatchingService matchingService;
    private final ca.joaoborges.finance.common.ImportCutoff importCutoff;

    private record AlertKey(Long categoryId, YearMonth month) {
    }

    /**
     * Incremental sync window for the scheduled run / "Sync now": from the start
     * of yesterday (UTC). It overlaps the prior day so nothing slips through the
     * daily boundary — the {@code simplefin_id} dedup makes the overlap free.
     */
    private Instant defaultStart() {
        return LocalDate.now(ZoneOffset.UTC).minusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    @Transactional
    public void setup(final String setupToken) {
        if (!StringUtils.hasText(setupToken)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Setup token is required");
        }
        final String accessUrl = simpleFinClient.claim(setupToken);
        final SimpleFinConnection connection = connectionRepository.findFirstByOrderByIdAsc()
                .orElseGet(SimpleFinConnection::new);
        connection.setAccessUrl(accessUrl);
        connectionRepository.save(connection);
    }

    @Transactional(readOnly = true)
    public SimpleFinConnection connection() {
        return connectionRepository.findFirstByOrderByIdAsc().orElse(null);
    }

    public boolean isConnected() {
        return connectionRepository.findFirstByOrderByIdAsc().isPresent();
    }

    /** Incremental sync over the default recent window (scheduled / "Sync now"). */
    @Transactional
    public ImportRun sync() {
        return sync(defaultStart(), null);
    }

    /**
     * Sync transactions posted in [startDate, endDate). endDate null = up to now.
     * Used for the forced custom-range import from the UI.
     */
    @Transactional
    public ImportRun sync(final Instant startDate, final Instant endDate) {
        final SimpleFinConnection connection = connectionRepository.findFirstByOrderByIdAsc()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "SimpleFIN is not connected"));
        final JsonNode root = objectMapper.readTree(
                simpleFinClient.fetchAccounts(connection.getAccessUrl(), startDate, endDate));

        final ImportRun run = importRunRepository.save(ImportRun.builder()
                .source(SourceType.SIMPLEFIN)
                .status(ImportStatus.RUNNING)
                .startedAt(Instant.now())
                .fileName("SimpleFIN sync")
                .build());

        final List<Rule> enabledRules = ruleRepository.findByEnabledTrueOrderByPriorityAscIdAsc();
        final Map<String, Integer> byAccount = new LinkedHashMap<>();
        final Map<AlertKey, BigDecimal> spendByCategoryMonth = new HashMap<>();
        final Map<Long, Category> categoriesById = new HashMap<>();
        int newCount = 0;
        int dedupCount = 0;
        int reviewed = 0;
        int needsReview = 0;

        final JsonNode accounts = root.path("accounts");
        for (int i = 0; i < accounts.size(); i++) {
            final JsonNode accountNode = accounts.get(i);
            final String simplefinId = accountNode.path("id").asString("");
            if (!StringUtils.hasText(simplefinId)) {
                continue;
            }
            final Account account = upsertAccount(accountNode, simplefinId);
            final Account canonical = account.getMergedInto() != null ? account.getMergedInto() : account;

            final JsonNode txns = accountNode.path("transactions");
            for (int j = 0; j < txns.size(); j++) {
                final JsonNode txNode = txns.get(j);
                if (txNode.path("pending").asBoolean(false)) {
                    continue;
                }
                final long posted = txNode.path("posted").asLong(0);
                final String txId = txNode.path("id").asString("");
                if (posted <= 0 || !StringUtils.hasText(txId)) {
                    continue;
                }
                final Instant postedAt = Instant.ofEpochSecond(posted);
                if (importCutoff.excludes(postedAt)) {
                    continue;
                }
                if (transactionRepository.existsBySimplefinId(txId)) {
                    dedupCount++;
                    continue;
                }

                final BigDecimal amount = new BigDecimal(txNode.path("amount").asString("0"));
                final String merchant = merchantOf(txNode, txId);
                final String hash = ContentHashing.of(canonical.getName(), postedAt, amount, merchant);
                final Transaction transaction = Transaction.builder()
                        .account(canonical)
                        .source(SourceType.SIMPLEFIN)
                        .simplefinId(txId)
                        .merchantName(merchant)
                        .amount(amount)
                        .postedAt(postedAt)
                        .currency(canonical.getCurrency())
                        .contentHash(hash)
                        .dedupKey(txId + ":" + posted)
                        .needsReview(true)
                        .importRun(run)
                        .build();

                ruleEngine.categorize(transaction, enabledRules);
                transactionRepository.save(transaction);
                matchingService.matchNewTransaction(transaction);
                newCount++;
                byAccount.merge(canonical.getName(), 1, Integer::sum);
                if (transaction.isNeedsReview()) {
                    needsReview++;
                } else {
                    reviewed++;
                }

                final Category category = transaction.getCategory();
                if (category != null && !category.isIncome()) {
                    final AlertKey key = new AlertKey(category.getId(), YearMonth.from(postedAt.atZone(ZoneOffset.UTC)));
                    spendByCategoryMonth.merge(key, amount.negate(), BigDecimal::add);
                    categoriesById.putIfAbsent(category.getId(), category);
                }
            }
        }

        run.setNewCount(newCount);
        run.setDedupCount(dedupCount);
        run.setAccountCount(byAccount.size());
        run.setStatus(ImportStatus.SUCCESS);
        run.setFinishedAt(Instant.now());
        final ImportRun saved = importRunRepository.save(run);

        connection.setLastSyncedAt(Instant.now());
        connectionRepository.save(connection);

        discordNotifier.sendImportSummary(new ImportSummary("SimpleFIN sync", newCount, reviewed, needsReview, byAccount));
        spendByCategoryMonth.forEach((key, delta) ->
                budgetAlertService.checkAfterSpend(categoriesById.get(key.categoryId()), key.month(), delta));
        return saved;
    }

    private Account upsertAccount(final JsonNode accountNode, final String simplefinId) {
        final String name = accountNode.path("name").asString(simplefinId);
        final String rawCurrency = accountNode.path("currency").asString("CAD");
        final String currency = rawCurrency.length() == 3 ? rawCurrency : "CAD";

        final Account account = accountRepository.findBySimplefinId(simplefinId)
                .orElseGet(() -> Account.builder()
                        .simplefinId(simplefinId)
                        .name(name)
                        .type(guessType(name))
                        .currency(currency)
                        .build());
        account.setCurrency(currency);
        final String balance = accountNode.path("balance").asString("");
        if (StringUtils.hasText(balance)) {
            account.setBalance(new BigDecimal(balance));
        }
        final long balanceDate = accountNode.path("balance-date").asLong(0);
        if (balanceDate > 0) {
            account.setBalanceDate(Instant.ofEpochSecond(balanceDate));
        }
        account.setLastSyncedAt(Instant.now());
        return accountRepository.save(account);
    }

    private String merchantOf(final JsonNode txNode, final String fallback) {
        final String payee = txNode.path("payee").asString("");
        if (StringUtils.hasText(payee)) {
            return payee;
        }
        final String description = txNode.path("description").asString("");
        return StringUtils.hasText(description) ? description : fallback;
    }

    private AccountType guessType(final String name) {
        final String lower = name.toLowerCase(Locale.ROOT);
        if (lower.contains("visa") || lower.contains("mastercard") || lower.contains("amex")
                || lower.contains("credit") || lower.contains("card")) {
            return AccountType.CREDIT_CARD;
        }
        if (lower.contains("saving")) {
            return AccountType.SAVINGS;
        }
        if (lower.contains("loan") || lower.contains("mortgage")) {
            return AccountType.LOAN;
        }
        return AccountType.CHECKING;
    }

}
