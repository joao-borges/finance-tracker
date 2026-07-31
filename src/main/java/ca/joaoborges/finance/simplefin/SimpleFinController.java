package ca.joaoborges.finance.simplefin;

import ca.joaoborges.finance.ingest.ImportRunDto;
import ca.joaoborges.finance.ingest.ImportRunMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * SimpleFIN setup and sync. The access URL lives only in the DB and is never
 * returned by any of these endpoints, including {@link #status()}.
 */
@RestController
@RequestMapping("/api/simplefin")
@RequiredArgsConstructor
public class SimpleFinController {

    private final SimpleFinSyncService syncService;
    private final SimpleFinStatusDigest statusDigest;
    private final ImportRunMapper importRunMapper;

    public record SetupRequest(String token) {
    }

    /** Claim a one-time setup token and store the resulting access URL. */
    @PostMapping("/setup")
    public SimpleFinStatus setup(@RequestBody final SetupRequest request) {
        try {
            syncService.setup(request == null ? null : request.token());
        } catch (final IllegalStateException upstream) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, upstream.getMessage(), upstream);
        }
        return status();
    }

    @GetMapping("/status")
    @Transactional(readOnly = true)
    public SimpleFinStatus status() {
        final SimpleFinConnection connection = syncService.connection();
        return new SimpleFinStatus(connection != null, connection == null ? null : connection.getLastSyncedAt());
    }

    /** On-demand bridge health check — same live probe as the daily digest (also posts to Discord). */
    @PostMapping("/health-check")
    public SimpleFinStatusDigest.Result healthCheck() {
        return statusDigest.send();
    }

    /**
     * Pull transactions and run them through the ingest pipeline. With no params
     * it syncs the recent window; pass {@code from} (and optionally {@code to},
     * ISO dates) to force a custom range.
     */
    @PostMapping("/sync")
    public ImportRunDto sync(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) final LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) final LocalDate to) {
        try {
            if (from == null) {
                return importRunMapper.toDto(syncService.sync());
            }
            final Instant start = from.atStartOfDay(ZoneOffset.UTC).toInstant();
            // end-date is exclusive at the bridge, so add a day to include all of `to`.
            final Instant end = to == null ? null : to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
            return importRunMapper.toDto(syncService.sync(start, end));
        } catch (final IllegalStateException upstream) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, upstream.getMessage(), upstream);
        }
    }

}
