package ca.joaoborges.finance.simplefin;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SyncWindowTest {

    private static final Instant NOW = Instant.parse("2026-09-16T12:00:00Z");

    private Instant daysAgo(final long days) {
        return NOW.minus(Duration.ofDays(days));
    }

    @Test
    void startsFromTheWatermarkWhenItSitsInsideBothClamps() {
        final Instant watermark = daysAgo(9);
        assertEquals(watermark, SyncWindow.startFrom(Optional.of(watermark), NOW));
    }

    /**
     * The Amex outage: the connection died and its watermark stopped nine days
     * back, so the window has to reach that far — a fixed 7-day lookback is what
     * stranded those rows permanently.
     */
    @Test
    void reachesBackOverAnOutageLongerThanTheOldFixedLookback() {
        assertEquals(daysAgo(9), SyncWindow.startFrom(Optional.of(daysAgo(9)), NOW));
    }

    @Test
    void alwaysRequestsTheMinimumOverlapEvenWhenFullyCaughtUp() {
        // A watermark of "now" would ask for nothing; late-posted rows need slack.
        assertEquals(NOW.minus(SyncWindow.MIN_OVERLAP), SyncWindow.startFrom(Optional.of(NOW), NOW));
    }

    @Test
    void clampsADeadAccountToTheMaximumLookback() {
        // A closed card the bridge still lists must not turn every sync into a
        // full-history pull.
        assertEquals(NOW.minus(SyncWindow.MAX_LOOKBACK),
                SyncWindow.startFrom(Optional.of(daysAgo(400)), NOW));
    }

    @Test
    void fallsBackToTheColdStartLookbackWithNoWatermark() {
        assertEquals(NOW.minus(SyncWindow.COLD_START_LOOKBACK), SyncWindow.startFrom(Optional.empty(), NOW));
    }

}
