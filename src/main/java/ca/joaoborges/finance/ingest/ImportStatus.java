package ca.joaoborges.finance.ingest;

public enum ImportStatus {

    RUNNING,
    SUCCESS,
    /**
     * The run finished, but not every account's data came through — the bridge
     * reported a connection problem, or an account's data didn't advance. Kept
     * distinct from {@link #SUCCESS} so a broken bank connection can't hide
     * behind a green run, and so the next sync knows it still has ground to
     * make up.
     */
    PARTIAL,
    FAILED

}
