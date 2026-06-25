package ca.joaoborges.finance.transaction;

import ca.joaoborges.finance.budget.BudgetAlertService;
import ca.joaoborges.finance.category.Category;
import ca.joaoborges.finance.category.CategoryRepository;
import ca.joaoborges.finance.common.PageResponse;
import ca.joaoborges.finance.merchant.Merchant;
import ca.joaoborges.finance.merchant.MerchantService;
import ca.joaoborges.finance.rule.RuleEngine;
import ca.joaoborges.finance.rule.RuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
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
    private final TransactionMapper transactionMapper;
    private final CategoryRepository categoryRepository;
    private final BudgetAlertService budgetAlertService;
    private final MerchantService merchantService;
    private final RuleEngine ruleEngine;
    private final RuleRepository ruleRepository;

    /** One leg of a split: an amount and the category it belongs to. */
    public record SplitLine(BigDecimal amount, Long categoryId) {
    }

    public record SplitRequest(List<SplitLine> splits) {
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
        final TransactionDto dto = transactionMapper.toDto(transactionRepository.save(transaction));

        final Category category = transaction.getCategory();
        if (category != null && !category.getId().equals(previousCategoryId)
                && !transaction.isExcludedFromBudget() && !transaction.isSplit() && !transaction.isDedup()) {
            budgetAlertService.checkAfterSpend(category,
                    YearMonth.from(transaction.getPostedAt().atZone(ZoneOffset.UTC)), transaction.getAmount().negate());
        }
        return dto;
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
