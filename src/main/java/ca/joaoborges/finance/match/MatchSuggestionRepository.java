package ca.joaoborges.finance.match;

import ca.joaoborges.finance.transaction.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

import java.math.BigDecimal;
import java.util.List;

@RepositoryRestResource(exported = false)
public interface MatchSuggestionRepository extends JpaRepository<MatchSuggestion, Long> {

    /** Open suggestions for the Matches page, newest first. */
    List<MatchSuggestion> findByDismissedFalseOrderByCreatedAtDesc();

    /**
     * True if this exact pair is already suggested (order-independent). Pairs, not
     * single legs, so a purchase can carry several refund suggestions (one-to-many).
     */
    @Query("""
            SELECT COUNT(s) > 0 FROM MatchSuggestion s
            WHERE (s.legA = :a AND s.legB = :b) OR (s.legA = :b AND s.legB = :a)
            """)
    boolean existsPair(@Param("a") Transaction a, @Param("b") Transaction b);

    /** True if the user already dismissed this pair — never re-suggest it. */
    @Query("""
            SELECT COUNT(s) > 0 FROM MatchSuggestion s
            WHERE s.dismissed = true
              AND ((s.legA = :a AND s.legB = :b) OR (s.legA = :b AND s.legB = :a))
            """)
    boolean pairDismissed(@Param("a") Transaction a, @Param("b") Transaction b);

    /** Open suggestions of a type with the transaction as either leg. */
    @Query("""
            SELECT s FROM MatchSuggestion s
            WHERE s.dismissed = false AND s.type = :type AND (s.legA = :tx OR s.legB = :tx)
            """)
    List<MatchSuggestion> findOpenByTypeInvolving(@Param("type") MatchType type, @Param("tx") Transaction tx);

    /**
     * Total inflow amount held against a purchase by OTHER refunds' open
     * suggestions — a soft reservation so several refunds don't all target the
     * same purchase past its amount. Excludes suggestions involving
     * {@code refund} so re-evaluating a refund never blocks its own target.
     */
    @Query("""
            SELECT COALESCE(SUM(CASE WHEN s.legA = :purchase THEN s.legB.amount ELSE s.legA.amount END), 0)
            FROM MatchSuggestion s
            WHERE s.dismissed = false AND s.type = ca.joaoborges.finance.match.MatchType.REFUND
              AND (s.legA = :purchase OR s.legB = :purchase)
              AND s.legA <> :refund AND s.legB <> :refund
            """)
    BigDecimal sumOpenSuggestionsAgainst(@Param("purchase") Transaction purchase, @Param("refund") Transaction refund);

    /** Drop any suggestions referencing a transaction once it's matched/unmatched. */
    @Modifying
    @Query("DELETE FROM MatchSuggestion s WHERE s.legA = :tx OR s.legB = :tx")
    void deleteInvolving(@Param("tx") Transaction tx);

}
