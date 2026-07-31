package ca.joaoborges.finance.simplefin;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Daily SimpleFIN sync, at noon America/Vancouver by default (override with
 * {@code finance.simplefin.sync-cron} / {@code finance.simplefin.sync-zone}).
 * Kept separate from {@link SimpleFinSyncService} so the scheduled call goes
 * through the Spring proxy and {@code @Transactional} on {@code sync()} applies.
 * Skips quietly when no connection is configured and never lets a sync failure
 * escape the worker.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SimpleFinScheduler {

    private final SimpleFinSyncService syncService;
    private final SimpleFinStatusDigest statusDigest;

    /** Daily 10:00 health digest to Discord (override with finance.simplefin.status-cron). */
    @Scheduled(cron = "${finance.simplefin.status-cron:0 0 10 * * *}",
            zone = "${finance.simplefin.sync-zone:America/Vancouver}")
    public void scheduledStatusDigest() {
        try {
            statusDigest.send();
        } catch (final RuntimeException failure) {
            log.warn("SimpleFIN status digest failed: {}", failure.getMessage());
        }
    }

    @Scheduled(cron = "${finance.simplefin.sync-cron:0 0 12 * * *}",
            zone = "${finance.simplefin.sync-zone:America/Vancouver}")
    public void scheduledSync() {
        if (!syncService.isConnected()) {
            return;
        }
        try {
            syncService.sync();
        } catch (final RuntimeException failure) {
            log.warn("Scheduled SimpleFIN sync failed: {}", failure.getMessage());
        }
    }

}
