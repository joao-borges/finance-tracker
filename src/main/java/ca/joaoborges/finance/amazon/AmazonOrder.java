package ca.joaoborges.finance.amazon;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * One Amazon order as the browser extension read it from the order invoice:
 * what was bought ({@link AmazonOrderItem}) and the card charges Amazon made
 * for it ({@link AmazonCharge}). Amazon charges per shipment, so an order
 * usually maps to several bank transactions — the charges carry those links.
 */
@Entity
@Table(name = "amazon_orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AmazonOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_number", nullable = false, unique = true, length = 32)
    private String orderNumber;

    /** Storefront host, e.g. {@code www.amazon.ca} — builds the link back to the order. */
    @Column(nullable = false, length = 64)
    private String marketplace;

    @Column(name = "ordered_on", nullable = false)
    private LocalDate orderedOn;

    @Column(precision = 19, scale = 4)
    private BigDecimal total;

    @Column(length = 3)
    private String currency;

    @Column(name = "imported_at", nullable = false)
    private Instant importedAt;

    @Builder.Default
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<AmazonOrderItem> items = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("chargedOn ASC, id ASC")
    private List<AmazonCharge> charges = new ArrayList<>();

}
