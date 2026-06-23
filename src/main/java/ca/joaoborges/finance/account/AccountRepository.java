package ca.joaoborges.finance.account;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

import java.util.List;
import java.util.Optional;

@RepositoryRestResource(exported = false)
public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByImportRef(String importRef);

    /** Accounts shown on the accounts page: canonical/standalone (not merged away). */
    List<Account> findByMergedIntoIsNullOrderByNameAsc();

}
