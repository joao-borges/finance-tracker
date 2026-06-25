package ca.joaoborges.finance.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

/**
 * Optional hard floor on how far back transactions may be imported. Configured
 * via {@code finance.import.min-posted-date} (an ISO date, UTC); when set, any
 * transaction posted before it is dropped by every ingest path (CSV and
 * SimpleFIN) before it can be persisted. Blank/unset means no floor.
 */
@Component
public class ImportCutoff {

    private static final Logger log = LoggerFactory.getLogger(ImportCutoff.class);

    private final Instant minPostedAt;

    public ImportCutoff(@Value("${finance.import.min-posted-date:}") final String minPostedDate) {
        Instant parsed = null;
        if (StringUtils.hasText(minPostedDate)) {
            parsed = LocalDate.parse(minPostedDate.trim()).atStartOfDay(ZoneOffset.UTC).toInstant();
            log.info("Import cutoff active: dropping transactions posted before {} (UTC)", minPostedDate.trim());
        }
        this.minPostedAt = parsed;
    }

    /** True when {@code postedAt} falls before the configured floor and must be skipped. */
    public boolean excludes(final Instant postedAt) {
        return minPostedAt != null && postedAt != null && postedAt.isBefore(minPostedAt);
    }

    public Optional<Instant> minPostedAt() {
        return Optional.ofNullable(minPostedAt);
    }

}
