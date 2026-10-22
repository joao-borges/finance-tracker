package ca.joaoborges.finance.amazon;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** An Amazon order with its items and charges; each charge carries the bank transaction it is linked to. */
@Builder
public record AmazonOrderDto(
        Long id,
        String orderNumber,
        String marketplace,
        String url,
        LocalDate orderedOn,
        BigDecimal total,
        String currency,
        List<Item> items,
        List<Charge> charges) {

    @Builder
    public record Item(String title, int quantity, BigDecimal unitPrice) {
    }

    @Builder
    public record Charge(
            Long id,
            LocalDate chargedOn,
            BigDecimal amount,
            String cardLabel,
            String cardLast4,
            boolean manual,
            Long transactionId,
            Instant transactionPostedAt,
            String transactionAccountName,
            Long transactionCategoryId) {
    }

}
