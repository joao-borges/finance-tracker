package ca.joaoborges.finance.common;

/**
 * Where a transaction or import run originated. SimpleFIN is the primary feed;
 * CSV is the fallback source that flows through the same ingest pipeline; MANUAL
 * is a single transaction typed in by the operator.
 */
public enum SourceType {

    SIMPLEFIN,
    CSV,
    MANUAL

}
