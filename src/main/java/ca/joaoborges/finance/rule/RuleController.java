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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

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
        return ruleRepository.findAllByOrderByPriorityAscIdAsc().stream().map(ruleMapper::toDto).toList();
    }

    @PostMapping
    @Transactional
    public RuleDto create(@RequestBody final RuleDto dto) {
        if (!StringUtils.hasText(dto.name()) || !StringUtils.hasText(dto.merchantMatch()) || dto.categoryId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name, merchantMatch and categoryId are required");
        }
        final Rule rule = ruleMapper.toEntity(dto);
        rule.setCategory(resolveCategory(dto.categoryId()));
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

    /** Retroactively run a single rule over all uncategorized transactions. */
    @PostMapping("/{id}/apply")
    @Transactional
    public ApplyResult apply(@PathVariable final Long id) {
        final Rule rule = ruleRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Rule not found"));
        final List<Rule> single = List.of(rule);
        int applied = 0;
        for (final Transaction transaction : transactionRepository.findByCategoryIsNullAndSplitFalseAndDedupFalse()) {
            if (ruleEngine.firstMatch(transaction.getMerchantName(), single).isPresent()) {
                ruleEngine.apply(transaction, rule);
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
