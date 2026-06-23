package ca.joaoborges.finance.common;

/**
 * Where a transaction or import run originated. SimpleFIN is the primary feed;
 * CSV is the fallback source that flows through the same ingest pipeline.
 */
public enum SourceType {

    SIMPLEFIN,
    CSV

}
