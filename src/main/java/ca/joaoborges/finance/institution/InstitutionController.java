package ca.joaoborges.finance.institution;

import ca.joaoborges.finance.account.Account;
import ca.joaoborges.finance.account.AccountRepository;
import ca.joaoborges.finance.account.OffBudgetService;
import ca.joaoborges.finance.common.FaviconService;
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

/**
 * Institutions: the grouping layer above accounts. Toggling an institution
 * off-budget cascades to all of its accounts (each with the same
 * apply-backwards semantics as the account-level toggle).
 */
@RestController
@RequestMapping("/api/institutions")
@RequiredArgsConstructor
public class InstitutionController {

    private final InstitutionRepository institutionRepository;
    private final InstitutionMapper institutionMapper;
    private final AccountRepository accountRepository;
    private final OffBudgetService offBudgetService;
    private final FaviconService faviconService;

    @GetMapping
    @Transactional(readOnly = true)
    public List<InstitutionDto> list() {
        return institutionRepository.findAllByOrderByNameAsc().stream().map(institutionMapper::toDto).toList();
    }

    @PostMapping
    @Transactional
    public InstitutionDto create(@RequestBody final InstitutionDto dto) {
        if (!StringUtils.hasText(dto.name())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name is required");
        }
        final Institution institution = institutionMapper.toEntity(dto);
        institution.setLogoUrl(faviconService.resolveLogoUrl(dto.website()));
        return institutionMapper.toDto(institutionRepository.save(institution));
    }

    @PatchMapping("/{id}")
    @Transactional
    public InstitutionDto update(@PathVariable final Long id, @RequestBody final InstitutionDto dto) {
        final Institution institution = institutionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Institution not found"));
        final boolean wasOffBudget = institution.isOffBudget();
        institutionMapper.update(institution, dto);
        if (dto.website() != null) {
            institution.setLogoUrl(faviconService.resolveLogoUrl(dto.website()));
        }
        final InstitutionDto saved = institutionMapper.toDto(institutionRepository.save(institution));
        if (wasOffBudget != institution.isOffBudget()) {
            // Cascade to the institution's accounts; enabling applies backwards.
            for (final Account account : accountRepository.findByInstitution(institution)) {
                account.setOffBudget(institution.isOffBudget());
                accountRepository.save(account);
                if (institution.isOffBudget()) {
                    offBudgetService.applyBackwards(account);
                }
            }
        }
        return saved;
    }

}
