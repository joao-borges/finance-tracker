package ca.joaoborges.finance.csv;

/**
 * How a CSV expresses transaction amounts: one signed column, or separate
 * debit/credit columns that get normalized to a single signed amount.
 */
public enum AmountSignConvention {

    SINGLE_SIGNED,
    SEPARATE_DEBIT_CREDIT

}
