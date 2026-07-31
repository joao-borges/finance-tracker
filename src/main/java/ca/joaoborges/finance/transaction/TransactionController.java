package ca.joaoborges.finance.transaction;

import ca.joaoborges.finance.account.Account;
import ca.joaoborges.finance.account.AccountRepository;
import ca.joaoborges.finance.budget.BudgetAlertService;
import ca.joaoborges.finance.category.Category;
import ca.joaoborges.finance.category.CategoryRepository;
import ca.joaoborges.finance.common.ContentHashing;
import ca.joaoborges.finance.common.PageResponse;
import ca.joaoborges.finance.common.SourceType;
import ca.joaoborges.finance.match.MatchingService;
import ca.joaoborges.finance.merchant.Merchant;
import ca.joaoborges.finance.merchant.MerchantService;
import ca.joaoborges.finance.rule.RuleEngine;
import ca.joaoborges.finance.rule.RuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * Transactions list with combinable filters (see {@link TransactionFilter}),
 * pagination for infinite scroll, and inline edits. Visible rows only; newest
 * first with a stable {@code id} tiebreaker.
 */
@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private static final int MAX_PAGE_SIZE = 200;

    private final TransactionRepository transactionRepository;
    private final DeletedTransactionKeyRepository deletedTransactionKeyRepository;
    private final TransactionMapper transactionMapper;
    private final AccountRepository accountRepository;
    private final CategoryRepository categoryRepository;
    private final BudgetAlertService budgetAlertService;
    private final MerchantService merchantService;
    private final RuleEngine ruleEngine;
    private final RuleRepository ruleRepository;
    private final MatchingService matchingService;

    /** One leg of a split: an amount and the category it belongs to. */
    public record SplitLine(BigDecimal amount, Long categoryId) {
    }

    public record SplitRequest(List<SplitLine> splits) {
    }

    /**
     * Payload for adding a single transaction by hand. {@code amount} is signed
     * (negative = outflow); {@code date} is the UTC posting day.
     */
    public record ManualTransactionRequest(Long accountId, LocalDate date, String description, BigDecimal amount,
                                           Long categoryId, Long merchantId, Boolean excludedFromBudget,
                                           Boolean awaitingRefund) {
    }

    /**
     * Manually add one transaction (source {@code MANUAL}) — for cash or anything
     * the automatic feeds miss. Runs the rule engine only when no category is
     * picked; matching is deliberately skipped so a hand-entered row is never
     * silently paired. A chosen category counts in the budget immediately.
     */
    @PostMapping
    @Transactional
    public TransactionDto create(@RequestBody final ManualTransactionRequest body) {
        if (body.accountId() == null || body.date() == null || body.amount() == null
                || !StringUtils.hasText(body.description())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Account, date, description and amount are required");
        }
        final Account account = accountRepository.findById(body.accountId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown account " + body.accountId()));
        final Instant postedAt = body.date().atStartOfDay(ZoneOffset.UTC).toInstant();
        final String merchantName = body.description().trim();
        final String hash = ContentHashing.of(String.valueOf(account.getId()), postedAt, body.amount(), merchantName);
        final Category category = body.categoryId() == null ? null : resolveCategory(body.categoryId());
        final Transaction transaction = Transaction.builder()
                .account(account)
                .source(SourceType.MANUAL)
                .merchantName(merchantName)
                .merchant(body.merchantId() == null ? null : merchantService.resolve(body.merchantId(), null))
                .amount(body.amount())
                .postedAt(postedAt)
                .sourcePostedAt(postedAt)
                .currency(account.getCurrency())
                .category(category)
                .contentHash(hash)
                .dedupKey("manual:" + hash)
                .needsReview(category == null && !account.isOffBudget())
                .excludedFromBudget(body.excludedFromBudget() != null
                        ? body.excludedFromBudget() : account.isOffBudget())
                .awaitingRefund(Boolean.TRUE.equals(body.awaitingRefund()))
                .build();
        if (category == null && !account.isOffBudget()) {
            ruleEngine.categorize(transaction, ruleRepository.findByEnabledTrueOrderByPriorityAscIdAsc());
        }
        final Transaction saved = transactionRepository.save(transaction);

        final Category effective = saved.getCategory();
        if (effective != null && !saved.isExcludedFromBudget() && !saved.isSplit()
                && !saved.isDedup() && !saved.isAwaitingRefund()) {
            budgetAlertService.checkAfterSpend(effective,
                    YearMonth.from(saved.getPostedAt().atZone(ZoneOffset.UTC)), saved.getAmount().negate());
        }
        return transactionMapper.toDto(saved);
    }

    @GetMapping
    @Transactional(readOnly = true)
    public PageResponse<TransactionDto> list(final TransactionFilter filter,
                                             @RequestParam(defaultValue = "0") final int page,
                                             @RequestParam(defaultValue = "25") final int size) {
        final int pageSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        final Pageable pageable = PageRequest.of(Math.max(page, 0), pageSize,
                Sort.by(Sort.Order.desc("postedAt"), Sort.Order.desc("id")));
        final Page<Transaction> result = transactionRepository.findAll(filter.toSpecification(), pageable);
        final List<TransactionDto> content = result.getContent().stream().map(transactionMapper::toDto).toList();
        return new PageResponse<>(content, result.getNumber(), pageSize, result.hasNext(), result.getTotalElements());
    }

    @PatchMapping("/{id}")
    @Transactional
    public TransactionDto update(@PathVariable final Long id, @RequestBody final TransactionUpdate body) {
        final Transaction transaction = transactionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found"));
        final Long previousCategoryId = transaction.getCategory() == null ? null : transaction.getCategory().getId();
        if (body.categoryId() != null) {
            transaction.setCategory(resolveCategory(body.categoryId()));
        }
        final Merchant merchant = merchantService.resolve(body.merchantId(), body.newMerchantName());
        if (merchant != null) {
            transaction.setMerchant(merchant);
        }
        if (body.needsReview() != null) {
            transaction.setNeedsReview(body.needsReview());
        }
        if (body.excludedFromBudget() != null) {
            transaction.setExcludedFromBudget(body.excludedFromBudget());
        }
        if (body.awaitingRefund() != null) {
            transaction.setAwaitingRefund(body.awaitingRefund());
        }
        if (body.postedAt() != null) {
            // Move the operator-visible date (and thus the budget month). Dedup
            // stays keyed on sourcePostedAt, so re-imports don't duplicate this row.
            transaction.setPostedAt(body.postedAt().atStartOfDay(ZoneOffset.UTC).toInstant());
        }
        final TransactionDto dto = transactionMapper.toDto(transactionRepository.save(transaction));

        final Category category = transaction.getCategory();
        if (category != null && !category.getId().equals(previousCategoryId)
                && !transaction.isExcludedFromBudget() && !transaction.isSplit() && !transaction.isDedup()) {
            budgetAlertService.checkAfterSpend(category,
                    YearMonth.from(transaction.getPostedAt().atZone(ZoneOffset.UTC)), transaction.getAmount().negate());
        }
        return dto;
    }

    /**
     * Delete a transaction outright. Split children go with their parent (a
     * child alone can't be deleted — un-split first); matches are detached so a
     * transfer partner or pending refunds revert cleanly; the dedup key is
     * tombstoned so the next SimpleFIN sync or a CSV re-import doesn't
     * resurrect the row.
     */
    @DeleteMapping("/{id}")
    @Transactional
    public void delete(@PathVariable final Long id) {
        final Transaction transaction = transactionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found"));
        if (transaction.getSplitParent() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "This row is part of a split — un-split the parent first");
        }
        final List<Transaction> children = transactionRepository.findBySplitParent(transaction);
        for (final Transaction child : children) {
            matchingService.detachForDeletion(child);
        }
        matchingService.detachForDeletion(transaction);
        transactionRepository.deleteAll(children);
        deletedTransactionKeyRepository.save(DeletedTransactionKey.builder()
                .dedupKey(transaction.getDedupKey())
                .build());
        transactionRepository.delete(transaction);
    }

    /** Quarantined duplicates, for the restore UI. */
    @GetMapping("/duplicates")
    @Transactional(readOnly = true)
    public List<TransactionDto> duplicates() {
        return transactionRepository.findByDedupTrueOrderByPostedAtDesc().stream().map(transactionMapper::toDto).toList();
    }

    /** Restore a quarantined duplicate: clear the dedup flag and run it through the rules. */
    @PostMapping("/{id}/restore")
    @Transactional
    public TransactionDto restore(@PathVariable final Long id) {
        final Transaction transaction = transactionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found"));
        if (!transaction.isDedup()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Transaction is not quarantined");
        }
        transaction.setDedup(false);
        ruleEngine.categorize(transaction, ruleRepository.findByEnabledTrueOrderByPriorityAscIdAsc());
        return transactionMapper.toDto(transactionRepository.save(transaction));
    }

    /**
     * Split a transaction into child rows of {@code (amount, category)}. The parent
     * is hidden ({@code is_split}); the children behave as normal transactions.
     * Re-running replaces any existing children.
     */
    @PostMapping("/{id}/split")
    @Transactional
    public TransactionDto split(@PathVariable final Long id, @RequestBody final SplitRequest request) {
        final Transaction parent = transactionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found"));
        if (parent.getSplitParent() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot split a split child");
        }
        if (request == null || request.splits() == null || request.splits().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one split line is required");
        }
        transactionRepository.deleteAll(transactionRepository.findBySplitParent(parent));

        final List<Transaction> children = new ArrayList<>();
        int index = 0;
        for (final SplitLine split : request.splits()) {
            if (split.amount() == null || split.categoryId() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Each split line needs an amount and a category");
            }
            children.add(Transaction.builder()
                    .account(parent.getAccount())
                    .source(parent.getSource())
                    .merchantName(parent.getMerchantName())
                    .merchant(parent.getMerchant())
                    .amount(split.amount())
                    .postedAt(parent.getPostedAt())
                    .currency(parent.getCurrency())
                    .category(resolveCategory(split.categoryId()))
                    .contentHash(parent.getContentHash() + "#s" + index)
                    .dedupKey(parent.getDedupKey() + ":s" + index)
                    .splitParent(parent)
                    .needsReview(false)
                    .importRun(parent.getImportRun())
                    .build());
            index++;
        }
        transactionRepository.saveAll(children);

        parent.setSplit(true);
        parent.setNeedsReview(false);
        return transactionMapper.toDto(transactionRepository.save(parent));
    }

    /** Undo a transfer/refund match: unlink both legs and return them to review. */
    @PostMapping("/{id}/unmatch")
    @Transactional
    public TransactionDto unmatch(@PathVariable final Long id) {
        return transactionMapper.toDto(matchingService.unmatch(id));
    }

    /** Undo a split: delete the children and make the parent a normal transaction again. */
    @PostMapping("/{id}/unsplit")
    @Transactional
    public TransactionDto unsplit(@PathVariable final Long id) {
        final Transaction parent = transactionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found"));
        transactionRepository.deleteAll(transactionRepository.findBySplitParent(parent));
        parent.setSplit(false);
        return transactionMapper.toDto(transactionRepository.save(parent));
    }

    private Category resolveCategory(final Long categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown category " + categoryId));
    }

}
