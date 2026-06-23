package ca.joaoborges.finance.merchant;

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

@RestController
@RequestMapping("/api/merchants")
@RequiredArgsConstructor
public class MerchantController {

    private final MerchantRepository merchantRepository;
    private final MerchantMapper merchantMapper;
    private final FaviconService faviconService;

    @GetMapping
    public List<MerchantDto> list() {
        return merchantRepository.findAll().stream().map(merchantMapper::toDto).toList();
    }

    @PostMapping
    @Transactional
    public MerchantDto create(@RequestBody final MerchantDto dto) {
        if (!StringUtils.hasText(dto.name())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name is required");
        }
        final Merchant merchant = merchantMapper.toEntity(dto);
        merchant.setLogoUrl(faviconService.resolveLogoUrl(dto.website()));
        return merchantMapper.toDto(merchantRepository.save(merchant));
    }

    @PatchMapping("/{id}")
    @Transactional
    public MerchantDto update(@PathVariable final Long id, @RequestBody final MerchantDto dto) {
        final Merchant merchant = merchantRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Merchant not found"));
        merchantMapper.update(merchant, dto);
        if (dto.website() != null) {
            merchant.setLogoUrl(faviconService.resolveLogoUrl(dto.website()));
        }
        return merchantMapper.toDto(merchantRepository.save(merchant));
    }

}
