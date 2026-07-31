package ca.joaoborges.finance.transaction;

import ca.joaoborges.finance.account.Account;
import ca.joaoborges.finance.category.Category;
import ca.joaoborges.finance.common.SourceType;
import ca.joaoborges.finance.ingest.ImportRun;
import ca.joaoborges.finance.match.MatchType;
import ca.joaoborges.finance.merchant.Merchant;
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

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A single transaction. Amounts are signed: negative = outflow, positive =
 * inflow (matches SimpleFIN natively).
 *
 * <p>{@code merchantName} is the raw descriptor the bank sent (what rules match
 * against); {@code merchant} is the canonical {@link Merchant} once resolved.
 *
 * <p>The boolean flags drive the two predicates that matter — the default
 * transaction list and the budget sum. See the flag-semantics table in
 * {@code DESIGN.md}; implement those predicates once in {@code common/}, never as
 * scattered {@code if}s.
 */
@Entity
@Table(name = "transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SourceType source;

    /** The SimpleFIN transaction id; null for CSV rows. */
    @Column(name = "simplefin_id")
    private String simplefinId;

    /**
     * Source-native dedup key: {@code simplefin_id + ':' + posted_ts} for
     * SimpleFIN, content hash for id-less rows.
     */
    @Column(name = "dedup_key", nullable = false)
    private String dedupKey;

    /**
     * Always-computed content hash ({@code account + date + amount +
     * normalized-merchant}) used for cross-source dedup.
     */
    @Column(name = "content_hash", nullable = false)
    private String contentHash;

    /**
     * Second dedup key over the RAW statement text (see
     * {@code ContentHashing.ofStatement}) — matches a bank CSV export against
     * the bridge's description where the payee-based hash cannot.
     */
    @Column(name = "statement_hash")
    private String statementHash;

    @Column(name = "posted_at", nullable = false)
    private Instant postedAt;

    /**
     * The posting date as the SOURCE reported it, pinned at ingest and never
     * edited. Dedup hashes key on this, so the operator can move
     * {@code postedAt} between budget months (manually or via a shift rule)
     * without re-imports duplicating the row.
     */
    @Column(name = "source_posted_at")
    private Instant sourcePostedAt;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    /** Raw descriptor from SimpleFIN payee/description or the CSV merchant column. */
    @Column(name = "merchant", nullable = false)
    private String merchantName;

    /** Canonical merchant once resolved (by a rule or manually); nullable. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "merchant_id")
    private Merchant merchant;

    /** Optional cleaned/normalized form. */
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Builder.Default
    @Column(name = "needs_review", nullable = false)
    private boolean needsReview = true;

    @Builder.Default
    @Column(name = "excluded_from_budget", nullable = false)
    private boolean excludedFromBudget = false;

    /** True on a split PARENT — hidden from lists and budget, replaced by its children. */
    @Builder.Default
    @Column(name = "is_split", nullable = false)
    private boolean split = false;

    /** Set on split CHILDREN — points at the parent transaction. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "split_parent_id")
    private Transaction splitParent;

    /** True on a quarantined duplicate — hidden from normal lists, restorable. */
    @Builder.Default
    @Column(name = "is_dedup", nullable = false)
    private boolean dedup = false;

    /**
     * Known-future refund: excluded from the current budget (a manual flag for
     * purchases you expect to be refunded later). See the flag table in DESIGN.md.
     */
    @Builder.Default
    @Column(name = "awaiting_refund", nullable = false)
    private boolean awaitingRefund = false;

    /** The paired transaction when this is a matched transfer or refund leg. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "matched_with_id")
    private Transaction matchedWith;

    /** Kind of match on {@code matchedWith}; null when unmatched. */
    @Enumerated(EnumType.STRING)
    @Column(name = "match_type")
    private MatchType matchType;

    private String notes;

    @Column(nullable = false)
    private String currency;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "import_run_id")
    private ImportRun importRun;

}
