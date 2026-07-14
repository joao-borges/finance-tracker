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

    /** Statement hashes of all live transactions, the second cross-source dedup key. */
    @Query("SELECT t.statementHash FROM Transaction t WHERE t.dedup = false AND t.statementHash IS NOT NULL")
    List<String> findLiveStatementHashes();

    /** Rows still missing the backfilled statement hash (see StatementHashBackfill). */
    List<Transaction> findByStatementHashIsNull();

    /** Uncategorized transactions eligible for a retroactive rule run. */
    List<Transaction> findByCategoryIsNullAndSplitFalseAndDedupFalse();

    /** Default transaction list: visible rows (no split parents, no quarantined dups), newest first. */
    List<Transaction> findBySplitFalseAndDedupFalseOrderByPostedAtDesc();

    /** Quarantined duplicates for the restore UI, newest first. */
    List<Transaction> findByDedupTrueOrderByPostedAtDesc();

    /** Children of a split parent (for re-split / unsplit). */
    List<Transaction> findBySplitParent(Transaction parent);

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
              AND t.split = false AND t.dedup = false
              AND t.excludedFromBudget = false AND t.awaitingRefund = false
            """)
    BigDecimal sumForCategoryInMonth(@Param("categoryId") Long categoryId,
                                     @Param("start") Instant start,
                                     @Param("end") Instant end);

    /**
     * Live (non-split, non-dedup), not-yet-matched transactions in a date window,
     * for the matcher. Chronological so scans process (and reserve) purchases
     * deterministically — earlier refunds claim first.
     */
    @Query("""
            SELECT t FROM Transaction t
            WHERE t.split = false AND t.dedup = false AND t.matchedWith IS NULL
              AND t.postedAt >= :start AND t.postedAt < :end
            ORDER BY t.postedAt ASC, t.id ASC
            """)
    List<Transaction> findMatchCandidates(@Param("start") Instant start, @Param("end") Instant end);

    /** Total of refunds already matched to a purchase (positive). Supports one-to-many refunds. */
    @Query("""
            SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t
            WHERE t.matchedWith = :purchase AND t.matchType = ca.joaoborges.finance.match.MatchType.REFUND
            """)
    BigDecimal sumRefundedAgainst(@Param("purchase") Transaction purchase);

}
