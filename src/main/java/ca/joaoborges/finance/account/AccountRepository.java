package ca.joaoborges.finance.account;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@RepositoryRestResource(exported = false)
public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByImportRef(String importRef);

    Optional<Account> findBySimplefinId(String simplefinId);

    /** Relink candidates after a bridge reconnect: same bank-reported name, different id. */
    List<Account> findBySimplefinNameAndSimplefinIdNot(String simplefinName, String simplefinId);

    /** Accounts shown on the accounts page: canonical/standalone (not merged away). */
    List<Account> findByMergedIntoIsNullOrderByNameAsc();

    /** Accounts of an institution (off-budget cascade). */
    List<Account> findByInstitution(ca.joaoborges.finance.institution.Institution institution);

    /**
     * The furthest-behind SimpleFIN account's watermark — where the next
     * incremental sync has to start so a connection that was dead for a while
     * gets backfilled instead of skipped. Archived and merged-away accounts are
     * excluded: a retired account must not pin the window open forever.
     * Null-valued (never-synced) accounts are ignored by MIN; the caller treats
     * an empty result as "no watermark yet".
     */
    @Query("""
            SELECT MIN(a.syncedThrough) FROM Account a
            WHERE a.simplefinId IS NOT NULL AND a.mergedInto IS NULL AND a.archived = false
            """)
    Optional<Instant> earliestSyncedThrough();

    /** SimpleFIN accounts still in play, for the post-sync staleness check. */
    List<Account> findBySimplefinIdIsNotNullAndMergedIntoIsNullAndArchivedFalse();

}
