package ca.joaoborges.finance.merchant;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

/**
 * Resolves a merchant for an action that may reference an existing one by id or
 * create a new one on the fly by name. Shared by the rules and transaction
 * controllers.
 */
@Service
@RequiredArgsConstructor
public class MerchantService {

    private final MerchantRepository merchantRepository;

    /**
     * Returns the existing merchant for {@code merchantId}, or finds/creates one
     * named {@code newMerchantName}, or {@code null} when neither is provided.
     */
    public Merchant resolve(final Long merchantId, final String newMerchantName) {
        if (merchantId != null) {
            return merchantRepository.findById(merchantId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown merchant " + merchantId));
        }
        if (StringUtils.hasText(newMerchantName)) {
            final String name = newMerchantName.trim();
            return merchantRepository.findByNameIgnoreCase(name)
                    .orElseGet(() -> merchantRepository.save(Merchant.builder().name(name).build()));
        }
        return null;
    }

}
