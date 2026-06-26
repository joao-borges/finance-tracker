package ca.joaoborges.finance.match;

import ca.joaoborges.finance.transaction.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

import java.util.List;

@RepositoryRestResource(exported = false)
public interface MatchSuggestionRepository extends JpaRepository<MatchSuggestion, Long> {

    /** Open suggestions for the Matches page, newest first. */
    List<MatchSuggestion> findByDismissedFalseOrderByCreatedAtDesc();

    /** True if a suggestion already involves either transaction (avoid duplicates). */
    @Query("""
            SELECT COUNT(s) > 0 FROM MatchSuggestion s
            WHERE s.legA = :a OR s.legB = :a OR s.legA = :b OR s.legB = :b
            """)
    boolean existsInvolving(@Param("a") Transaction a, @Param("b") Transaction b);

    /** Drop any suggestions referencing a transaction once it's matched/unmatched. */
    @Modifying
    @Query("DELETE FROM MatchSuggestion s WHERE s.legA = :tx OR s.legB = :tx")
    void deleteInvolving(@Param("tx") Transaction tx);

}
