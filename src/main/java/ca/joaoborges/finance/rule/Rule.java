package ca.joaoborges.finance.rule;

import ca.joaoborges.finance.category.Category;
import ca.joaoborges.finance.common.CacheRegions;
import ca.joaoborges.finance.merchant.Merchant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * A phase-1 rule: case-insensitive {@code merchantName contains merchantMatch}
 * → set category (and optionally associate a canonical {@link Merchant}).
 * Evaluated by ascending priority; first match wins.
 */
@Entity
@Table(name = "rules")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE, region = CacheRegions.RULES)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Rule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "merchant_match", nullable = false)
    private String merchantMatch;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    /** Optional: also link matched transactions to this canonical merchant. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "merchant_id")
    private Merchant merchant;

    @Builder.Default
    @Column(name = "auto_approve", nullable = false)
    private boolean autoApprove = false;

    @Builder.Default
    @Column(nullable = false)
    private int priority = 0;

    @Builder.Default
    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "last_matched_at")
    private Instant lastMatchedAt;

    @Builder.Default
    @Column(name = "match_count", nullable = false)
    private long matchCount = 0;

}
