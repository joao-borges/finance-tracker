package ca.joaoborges.finance.transaction;

import ca.joaoborges.finance.account.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@RepositoryRestResource(exported = false)
public interface TransactionRepository extends JpaRepository<Transaction, Long>, JpaSpecificationExecutor<Transaction> {

    /** Review-queue size (visible, non-quarantined, non-split). */
    long countByNeedsReviewTrueAndSplitFalseAndDedupFalse();

    /** SimpleFIN dedup: has this provider transaction id already been imported? */
    boolean existsBySimplefinId(String simplefinId);

    /** Content hashes of all live (non-quarantined) transactions, for cross-source dedup. */
    @Query("SELECT t.contentHash FROM Transaction t WHERE t.dedup = false")
    List<String> findLiveContentHashes();

    /** Uncategorized transactions eligible for a retroactive rule run. */
    List<Transaction> findByCategoryIsNullAndSplitFalseAndDedupFalse();

    /** Default transaction list: visible rows (no split parents, no quarantined dups), newest first. */
    List<Transaction> findBySplitFalseAndDedupFalseOrderByPostedAtDesc();

    /** Reassign all of a merged source account's transactions to the canonical account. */
    @Modifying
    @Query("UPDATE Transaction t SET t.account = :target WHERE t.account = :source")
    int reassignAccount(@Param("source") Account source, @Param("target") Account target);

    /**
     * Actual spend for a category in a month — mirrors the budget predicate in
     * {@link ca.joaoborges.finance.common.TransactionPredicates#COUNTS_IN_BUDGET}.
     */
    @Query("""
            SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t
            WHERE t.category.id = :categoryId
              AND t.postedAt >= :start AND t.postedAt < :end
              AND t.split = false AND t.dedup = false AND t.excludedFromBudget = false
            """)
    BigDecimal sumForCategoryInMonth(@Param("categoryId") Long categoryId,
                                     @Param("start") Instant start,
                                     @Param("end") Instant end);

}
