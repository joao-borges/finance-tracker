package ca.joaoborges.finance.amazon;

import lombok.Builder;

/** Outcome of one extension import: what arrived, and how many charges found their bank row. */
@Builder
public record AmazonImportResult(int orders, int newOrders, int charges, int matched, int unmatched) {
}
