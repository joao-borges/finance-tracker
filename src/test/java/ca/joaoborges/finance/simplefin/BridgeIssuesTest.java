package ca.joaoborges.finance.simplefin;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BridgeIssuesTest {

    @Test
    void firstSightingOfAnIssueAlerts() {
        final String signature = BridgeIssues.signature(List.of("Connection expires in 45 days"));
        assertTrue(BridgeIssues.shouldNotify(signature, null));
    }

    /** The daily repeat of a standing warning — the noise this exists to stop. */
    @Test
    void theSameIssueWithADifferentCountdownDoesNotAlertAgain() {
        final String yesterday = BridgeIssues.signature(List.of("Connection expires in 45 days"));
        final String today = BridgeIssues.signature(List.of("Connection expires in 44 days"));
        assertFalse(BridgeIssues.shouldNotify(today, yesterday));
    }

    @Test
    void aNewIssueAlongsideAKnownOneAlerts() {
        final String known = BridgeIssues.signature(List.of("Connection expires in 45 days"));
        final String now = BridgeIssues.signature(List.of("Connection expires in 44 days", "Amex: auth required"));
        assertTrue(BridgeIssues.shouldNotify(now, known));
    }

    @Test
    void orderDoesNotMatter() {
        final String first = BridgeIssues.signature(List.of("A", "B"));
        final String second = BridgeIssues.signature(List.of("B", "A"));
        assertFalse(BridgeIssues.shouldNotify(second, first));
    }

    /** A clean run clears the signature, so the same issue coming back alerts again. */
    @Test
    void aCleanRunNeverAlertsAndResetsTheSignature() {
        final String clean = BridgeIssues.signature(List.of());
        assertNull(clean);
        assertFalse(BridgeIssues.shouldNotify(clean, "A"));
        assertTrue(BridgeIssues.shouldNotify(BridgeIssues.signature(List.of("A")), clean));
    }

}
