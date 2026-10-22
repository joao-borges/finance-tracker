package ca.joaoborges.finance.amazon;

import ca.joaoborges.finance.transaction.Transaction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import java.time.LocalDate;

/**
 * One card charge Amazon lists on an order's invoice ({@code amount} is
 * positive). {@code transaction} is the bank row it became, found by
 * {@link AmazonChargeMatcher} or linked by hand; {@code manual} pins a hand
 * link (or a deliberate unlink) so re-matching never overrides it.
 */
@Entity
@Table(name = "amazon_charges")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AmazonCharge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private AmazonOrder order;

    @Column(name = "charged_on", nullable = false)
    private LocalDate chargedOn;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "card_label", length = 128)
    private String cardLabel;

    @Column(name = "card_last4", length = 4)
    private String cardLast4;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id")
    private Transaction transaction;

    @Builder.Default
    @Column(nullable = false)
    private boolean manual = false;

    /** Identity of the charge on the card, independent of which order listed it. */
    public AmazonChargeMatcher.ChargeKey key() {
        return new AmazonChargeMatcher.ChargeKey(chargedOn, amount, cardLast4);
    }

}
