package ca.joaoborges.finance.account;

import ca.joaoborges.finance.common.CacheRegions;
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
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A bank account. Balance is pulled from SimpleFIN and stored as-is for
 * display — no reconciliation, no running balance. An optional {@code website}
 * resolves to a {@code logoUrl} (favicon) for easy identification.
 */
@Entity
@Table(name = "accounts")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE, region = CacheRegions.ACCOUNTS)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "simplefin_id", unique = true)
    private String simplefinId;

    /** Stable key CSV imports match on; {@code name} can be renamed for display. */
    @Column(name = "import_ref", unique = true)
    private String importRef;

    @Column(nullable = false)
    private String name;

    /**
     * When set, this account was merged into a canonical account (e.g. an Amex
     * supplementary card folded into the main one): it's hidden and its
     * transactions live on the target, but it's kept so imports keep matching.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "merged_into_id")
    private Account mergedInto;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountType type;

    @Column(nullable = false)
    private String currency;

    @Column(precision = 19, scale = 4)
    private BigDecimal balance;

    @Column(name = "balance_date")
    private Instant balanceDate;

    private String website;

    @Column(name = "logo_url")
    private String logoUrl;

    @Builder.Default
    @Column(nullable = false)
    private boolean hidden = false;

    @Builder.Default
    @Column(nullable = false)
    private boolean archived = false;

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;

}
