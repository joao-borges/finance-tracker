package ca.joaoborges.finance.csv;

/**
 * Supported CSV import formats. SIMPLE is the bootstrap {@code account,name,value}
 * format; the rest are real bank exports.
 */
public enum CsvFormat {

    SIMPLE,
    AMEX,
    RBC,
    PC_FINANCIAL

}
