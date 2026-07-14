package ca.joaoborges.finance.rule;

import ca.joaoborges.finance.category.Category;
import ca.joaoborges.finance.category.CategoryRepository;
import ca.joaoborges.finance.merchant.Merchant;
import ca.joaoborges.finance.merchant.MerchantService;
import ca.joaoborges.finance.transaction.Transaction;
import ca.joaoborges.finance.transaction.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/rules")
@RequiredArgsConstructor
public class RuleController {

    private final RuleRepository ruleRepository;
    private final CategoryRepository categoryRepository;
    private final MerchantService merchantService;
    private final TransactionRepository transactionRepository;
    private final RuleMapper ruleMapper;
    private final RuleEngine ruleEngine;

    /** Result of a retroactive rule run. */
    public record ApplyResult(int applied) {
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<RuleDto> list() {
        return ruleRepository.findAllByOrderByNameAsc().stream().map(ruleMapper::toDto).toList();
    }

    @PostMapping
    @Transactional
    public RuleDto create(@RequestBody final RuleDto dto) {
        if (!StringUtils.hasText(dto.name()) || !StringUtils.hasText(dto.merchantMatch())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name and merchantMatch are required");
        }
        final boolean hasMerchant = dto.merchantId() != null || StringUtils.hasText(dto.newMerchantName());
        if (dto.categoryId() == null && !hasMerchant && !Boolean.TRUE.equals(dto.autoApprove())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A rule needs at least one action: a category, a merchant, or auto-approve");
        }
        final Rule rule = ruleMapper.toEntity(dto);
        rule.setCategory(dto.categoryId() == null ? null : resolveCategory(dto.categoryId()));
        rule.setMerchant(resolveMerchant(dto));
        return ruleMapper.toDto(ruleRepository.save(rule));
    }

    @PatchMapping("/{id}")
    @Transactional
    public RuleDto update(@PathVariable final Long id, @RequestBody final RuleDto dto) {
        final Rule rule = ruleRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Rule not found"));
        ruleMapper.update(rule, dto);
        if (dto.categoryId() != null) {
            rule.setCategory(resolveCategory(dto.categoryId()));
        }
        final Merchant merchant = resolveMerchant(dto);
        if (merchant != null) {
            rule.setMerchant(merchant);
        }
        return ruleMapper.toDto(ruleRepository.save(rule));
    }

    @DeleteMapping("/{id}")
    @Transactional
    public void delete(@PathVariable final Long id) {
        if (!ruleRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Rule not found");
        }
        ruleRepository.deleteById(id);
    }

    /**
     * Retroactively run a single rule over the transactions its actions can still
     * improve: uncategorized rows for a category rule, merchant-less rows for a
     * merchant rule, unreviewed rows for an approve-only rule. Never flips an
     * already-reviewed row back into the review queue.
     */
    @PostMapping("/{id}/apply")
    @Transactional
    public ApplyResult apply(@PathVariable final Long id) {
        final Rule rule = ruleRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Rule not found"));
        final List<Rule> single = List.of(rule);
        final Set<Transaction> candidates = new LinkedHashSet<>();
        if (rule.getCategory() != null) {
            candidates.addAll(transactionRepository.findByCategoryIsNullAndSplitFalseAndDedupFalse());
        }
        if (rule.getMerchant() != null) {
            candidates.addAll(transactionRepository.findByMerchantIsNullAndSplitFalseAndDedupFalse());
        }
        if (rule.getCategory() == null && rule.getMerchant() == null) {
            candidates.addAll(transactionRepository.findByNeedsReviewTrueAndSplitFalseAndDedupFalse());
        }
        int applied = 0;
        for (final Transaction transaction : candidates) {
            if (ruleEngine.firstMatch(transaction.getMerchantName(), single).isPresent()) {
                final boolean alreadyReviewed = !transaction.isNeedsReview();
                ruleEngine.apply(transaction, rule);
                if (alreadyReviewed) {
                    transaction.setNeedsReview(false);
                }
                transactionRepository.save(transaction);
                applied++;
            }
        }
        return new ApplyResult(applied);
    }

    private Category resolveCategory(final Long categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown category " + categoryId));
    }

    private Merchant resolveMerchant(final RuleDto dto) {
        return merchantService.resolve(dto.merchantId(), dto.newMerchantName());
    }

}
