package ca.joaoborges.finance.simplefin;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Twice-daily SimpleFIN sync (06:00 and 18:00 server time). Kept separate from
 * {@link SimpleFinSyncService} so the scheduled call goes through the Spring
 * proxy and {@code @Transactional} on {@code sync()} applies. Skips quietly when
 * no connection is configured and never lets a sync failure escape the worker.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SimpleFinScheduler {

    private final SimpleFinSyncService syncService;

    @Scheduled(cron = "0 0 6,18 * * *")
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
