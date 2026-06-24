package ca.joaoborges.finance.transaction;

import ca.joaoborges.finance.budget.BudgetAlertService;
import ca.joaoborges.finance.category.Category;
import ca.joaoborges.finance.category.CategoryRepository;
import ca.joaoborges.finance.common.PageResponse;
import ca.joaoborges.finance.merchant.Merchant;
import ca.joaoborges.finance.merchant.MerchantService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.YearMonth;
import java.time.ZoneOffset;
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

    private Category resolveCategory(final Long categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown category " + categoryId));
    }

}
