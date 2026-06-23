package ca.joaoborges.finance.merchant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

import java.util.Optional;

@RepositoryRestResource(exported = false)
public interface MerchantRepository extends JpaRepository<Merchant, Long> {

    Optional<Merchant> findByNameIgnoreCase(String name);

}
