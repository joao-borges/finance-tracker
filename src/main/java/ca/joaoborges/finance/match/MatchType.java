package ca.joaoborges.finance.match;

/**
 * How two transactions are paired. TRANSFER = internal money movement between
 * own accounts (incl. credit-card payments), both legs excluded from budget.
 * REFUND = an inflow that offsets an earlier purchase on the same account.
 */
public enum MatchType {

    TRANSFER,
    REFUND

}
