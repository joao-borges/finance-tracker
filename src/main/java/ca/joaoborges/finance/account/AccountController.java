package ca.joaoborges.finance.account;

import ca.joaoborges.finance.common.FaviconService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final AccountMapper accountMapper;
    private final FaviconService faviconService;

    /** Target for a merge: the canonical account a source is folded into. */
    public record MergeRequest(Long targetId) {
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<AccountDto> list(@RequestParam(name = "includeMerged", defaultValue = "false") final boolean includeMerged) {
        final List<Account> accounts = includeMerged
                ? accountRepository.findAll()
                : accountRepository.findByMergedIntoIsNullOrderByNameAsc();
        return accounts.stream().map(accountMapper::toDto).toList();
    }

    @PostMapping
    @Transactional
    public AccountDto create(@RequestBody final AccountDto dto) {
        if (!StringUtils.hasText(dto.name()) || dto.type() == null || !StringUtils.hasText(dto.currency())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name, type and currency are required");
        }
        final Account account = accountMapper.toEntity(dto);
        account.setLogoUrl(faviconService.resolveLogoUrl(dto.website()));
        return accountMapper.toDto(accountRepository.save(account));
    }

    @PatchMapping("/{id}")
    @Transactional
    public AccountDto update(@PathVariable final Long id, @RequestBody final AccountDto dto) {
        final Account account = accountRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
        accountMapper.update(account, dto);
        if (dto.website() != null) {
            account.setLogoUrl(faviconService.resolveLogoUrl(dto.website()));
        }
        return accountMapper.toDto(accountRepository.save(account));
    }

    /**
     * Merge a source account into a target (canonical) account: hide the source,
     * keep it for import matching, and reassign its transactions to the target.
     */
    @PostMapping("/{id}/merge")
    @Transactional
    public AccountDto merge(@PathVariable final Long id, @RequestBody final MergeRequest request) {
        if (request.targetId() == null || request.targetId().equals(id)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A different target account is required");
        }
        final Account source = accountRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
        final Account target = accountRepository.findById(request.targetId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown target account"));
        if (target.getMergedInto() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Target account is itself merged");
        }
        source.setMergedInto(target);
        source.setHidden(true);
        accountRepository.save(source);
        transactionRepository.reassignAccount(source, target);
        return accountMapper.toDto(target);
    }

}
