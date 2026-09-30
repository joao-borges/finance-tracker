package ca.joaoborges.finance.simplefin;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Decides whether the bridge's reported issues are worth a Discord alert.
 *
 * <p>The bridge repeats a standing problem (an expiring connection, a limited
 * history window) on every sync, often with a countdown in the text. Alerting
 * each time turns a warning into noise, so an alert goes out only when the set
 * of issues differs from the one last sent. Digits are masked in the signature
 * so "expires in 45 days" and "expires in 44 days" count as the same issue.
 * Once the bridge reports clean, the signature clears and a recurrence alerts
 * again.
 *
 * <p>Pure so the comparison is testable without a bridge or a database.
 */
final class BridgeIssues {

    private BridgeIssues() {
    }

    /** Order- and number-insensitive signature; null when there are no issues. */
    static String signature(final List<String> issues) {
        if (issues.isEmpty()) {
            return null;
        }
        return issues.stream()
                .map(issue -> issue.replaceAll("\\d+", "#").strip())
                .distinct()
                .sorted()
                .collect(Collectors.joining("\n"));
    }

    static boolean shouldNotify(final String signature, final String lastNotified) {
        return signature != null && !Objects.equals(signature, lastNotified);
    }

}
