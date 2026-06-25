package ca.joaoborges.finance.category;

import ca.joaoborges.finance.common.CacheRegions;
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

import java.math.BigDecimal;

/**
 * A spending or income category. {@code isIncome} separates income lines from
 * expense lines in the budget math.
 */
@Entity
@Table(name = "categories")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE, region = CacheRegions.CATEGORIES)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    private CategoryGroup group;

    @Column(nullable = false)
    private String name;

    private String icon;

    @Builder.Default
    @Column(name = "is_income", nullable = false)
    private boolean income = false;

    @Builder.Default
    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    @Builder.Default
    @Column(nullable = false)
    private boolean archived = false;

    /**
     * Hidden from the budget page (decluttering) — distinct from archived.
     * Setting a planned budget amount auto-unhides it; only the Categories page
     * hides it.
     */
    @Builder.Default
    @Column(nullable = false)
    private boolean hidden = false;

    /** Optional monthly spend threshold; crossing it fires a yellow Discord alert. */
    @Column(name = "alert_threshold", precision = 19, scale = 4)
    private BigDecimal alertThreshold;

}
