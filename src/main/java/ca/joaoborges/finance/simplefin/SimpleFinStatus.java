package ca.joaoborges.finance.simplefin;

import java.time.Instant;

/** Connection status for the UI — never exposes the access URL. */
public record SimpleFinStatus(boolean connected, Instant lastSyncedAt) {
}
