package ca.joaoborges.finance.amazon;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * What the browser extension scraped from Amazon: orders with their items and
 * the card charges each invoice lists. {@code marketplace} is the storefront
 * host, e.g. {@code www.amazon.ca}.
 */
public record AmazonImportRequest(String marketplace, List<Order> orders) {

    public record Order(String orderNumber, LocalDate orderedOn, BigDecimal total, String currency,
                        List<Item> items, List<Charge> charges) {
    }

    public record Item(String title, Integer quantity, BigDecimal unitPrice) {
    }

    /** {@code amount} is positive — what the card was charged. */
    public record Charge(LocalDate chargedOn, BigDecimal amount, String cardLabel, String cardLast4) {
    }

}
