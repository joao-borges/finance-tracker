package ca.joaoborges.finance.simplefin;

import ca.joaoborges.finance.account.Account;
import ca.joaoborges.finance.account.AccountRepository;
import ca.joaoborges.finance.common.SourceType;
import ca.joaoborges.finance.ingest.ImportRun;
import ca.joaoborges.finance.ingest.ImportRunRepository;
import ca.joaoborges.finance.webhook.DiscordNotifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Daily SimpleFIN health digest for Discord: the last sync's outcome plus any
 * linked account whose balance feed has gone stale at the bridge. A healthy
 * bridge refreshes every account's balance daily even when there are no new
 * transactions, so a stale {@code balance_date} is the reliable signal for the
 * silent failure mode where a bank connection stops delivering without ever
 * reporting an error.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SimpleFinStatusDigest {

    private static final Duration STALE_AFTER = Duration.ofHours(48);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("MMM d, HH:mm");

    private final SimpleFinConnectionRepository connectionRepository;
    private final AccountRepository accountRepository;
    private final ImportRunRepository importRunRepository;
    private final DiscordNotifier discordNotifier;

    @Transactional(readOnly = true)
    public void send() {
        final SimpleFinConnection connection = connectionRepository.findFirstByOrderByIdAsc().orElse(null);
        if (connection == null) {
            return;
        }
        final ZoneId zone = ZoneId.of("America/Vancouver");
        final StringBuilder body = new StringBuilder();

        final ImportRun lastSync = importRunRepository
                .findFirstBySourceOrderByStartedAtDesc(SourceType.SIMPLEFIN).orElse(null);
        if (lastSync == null) {
            body.append("No SimpleFIN sync has run yet.");
        } else {
            body.append("Last sync ").append(DAY.format(lastSync.getStartedAt().atZone(zone)))
                    .append(" — ").append(lastSync.getNewCount()).append(" new, ")
                    .append(lastSync.getDedupCount()).append(" dedup, ")
                    .append(lastSync.getErrorCount()).append(" bridge issue(s).");
        }

        final List<String> stale = new ArrayList<>();
        for (final Account account : accountRepository.findByMergedIntoIsNullOrderByNameAsc()) {
            if (account.getSimplefinId() == null || account.isArchived()) {
                continue;
            }
            final Instant balanceDate = account.getBalanceDate();
            if (balanceDate == null || balanceDate.isBefore(Instant.now().minus(STALE_AFTER))) {
                stale.add(account.getName()
                        + (balanceDate == null ? " (never)" : " (stale since " + DAY.format(balanceDate.atZone(zone)) + ")"));
            }
        }
        final boolean syncErrored = lastSync != null && lastSync.getErrorCount() > 0;
        final boolean healthy = stale.isEmpty() && !syncErrored;
        if (stale.isEmpty()) {
            body.append("\nAll linked accounts fresh.");
        } else {
            body.append("\nStale balance feeds — check the bridge:");
            for (final String name : stale) {
                body.append("\n• ").append(name);
            }
        }

        log.info("SimpleFIN daily digest (healthy={}): {}", healthy, body.toString().replace('\n', ' '));
        discordNotifier.sendDailyStatus(body.toString(), healthy);
    }

}
