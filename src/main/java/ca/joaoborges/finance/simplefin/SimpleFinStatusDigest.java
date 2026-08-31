package ca.joaoborges.finance.simplefin;

import ca.joaoborges.finance.common.SourceType;
import ca.joaoborges.finance.ingest.ImportRun;
import ca.joaoborges.finance.ingest.ImportRunRepository;
import ca.joaoborges.finance.webhook.DiscordNotifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * On-demand SimpleFIN health check for the Imports page, built from a LIVE bridge probe
 * ({@code balances-only=1} — accounts and balances, no transactions), not from
 * whatever the last sync left in the database. A healthy bridge refreshes
 * every account's balance daily even with no new transactions, so a stale
 * balance date straight from the bridge is the reliable signal for the silent
 * failure mode where a bank connection stops delivering without ever
 * reporting an error. The last sync's outcome is included as context.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SimpleFinStatusDigest {

    private static final Duration STALE_AFTER = Duration.ofHours(48);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("MMM d, HH:mm");

    private final SimpleFinConnectionRepository connectionRepository;
    private final SimpleFinClient simpleFinClient;
    private final ObjectMapper objectMapper;
    private final ImportRunRepository importRunRepository;
    private final DiscordNotifier discordNotifier;

    /** Outcome of one health check — what the on-demand endpoint returns. */
    public record Result(boolean healthy, String report) {
    }

    @Transactional(readOnly = true)
    public Result send() {
        final SimpleFinConnection connection = connectionRepository.findFirstByOrderByIdAsc().orElse(null);
        if (connection == null) {
            return new Result(false, "SimpleFIN is not connected.");
        }
        final ZoneId zone = ZoneId.of("America/Vancouver");
        final StringBuilder body = new StringBuilder();

        final JsonNode root;
        try {
            root = objectMapper.readTree(simpleFinClient.fetchBalances(connection.getAccessUrl()));
        } catch (final RuntimeException unreachable) {
            log.warn("SimpleFIN health check: bridge unreachable: {}", unreachable.getMessage());
            final Result down = new Result(false, "Bridge unreachable: " + unreachable.getMessage());
            discordNotifier.sendHealthStatus(down.report(), false);
            return down;
        }

        final List<String> issues = new ArrayList<>();
        for (final JsonNode error : root.path("errors")) {
            issues.add(error.asString(""));
        }

        final List<String> stale = new ArrayList<>();
        int freshCount = 0;
        for (final JsonNode account : root.path("accounts")) {
            final String name = account.path("name").asString("?");
            final long balanceDate = account.path("balance-date").asLong(0);
            if (balanceDate > 0 && !Instant.ofEpochSecond(balanceDate).isBefore(Instant.now().minus(STALE_AFTER))) {
                freshCount++;
            } else {
                stale.add(name + (balanceDate <= 0
                        ? " (no balance date)"
                        : " (stale since " + DAY.format(Instant.ofEpochSecond(balanceDate).atZone(zone)) + ")"));
            }
        }

        if (!issues.isEmpty()) {
            body.append("Bridge issues:");
            for (final String issue : issues) {
                body.append("\n• ").append(issue);
            }
            body.append('\n');
        }
        if (stale.isEmpty()) {
            body.append("All ").append(freshCount).append(" bridge account(s) fresh.");
        } else {
            body.append(freshCount).append(" fresh, ").append(stale.size())
                    .append(" stale — check the bridge:");
            for (final String name : stale) {
                body.append("\n• ").append(name);
            }
        }

        final ImportRun lastSync = importRunRepository
                .findFirstBySourceOrderByStartedAtDesc(SourceType.SIMPLEFIN).orElse(null);
        if (lastSync != null) {
            body.append("\nLast sync ").append(DAY.format(lastSync.getStartedAt().atZone(zone)))
                    .append(" — ").append(lastSync.getNewCount()).append(" new, ")
                    .append(lastSync.getDedupCount()).append(" dedup, ")
                    .append(lastSync.getErrorCount()).append(" issue(s).");
        }

        final boolean healthy = issues.isEmpty() && stale.isEmpty();
        log.info("SimpleFIN health check (healthy={}): {}", healthy, body.toString().replace('\n', ' '));
        discordNotifier.sendHealthStatus(body.toString(), healthy);
        return new Result(healthy, body.toString());
    }

}
