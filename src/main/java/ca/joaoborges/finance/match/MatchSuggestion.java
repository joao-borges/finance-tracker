package ca.joaoborges.finance.match;

import ca.joaoborges.finance.transaction.Transaction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * A proposed (medium-confidence) match the matcher couldn't auto-apply, awaiting
 * the user's Confirm/Reject on the Matches page. Confirming applies the match and
 * deletes the suggestion; rejecting marks it dismissed so it isn't proposed again.
 */
@Entity
@Table(name = "match_suggestion")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MatchSuggestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "leg_a_id", nullable = false)
    private Transaction legA;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "leg_b_id", nullable = false)
    private Transaction legB;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MatchType type;

    @Builder.Default
    @Column(nullable = false)
    private boolean dismissed = false;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

}
