package ca.joaoborges.finance.simplefin;

import ca.joaoborges.finance.ingest.ImportRunDto;
import ca.joaoborges.finance.ingest.ImportRunMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * SimpleFIN setup and sync. The access URL lives only in the DB and is never
 * returned by any of these endpoints, including {@link #status()}.
 */
@RestController
@RequestMapping("/api/simplefin")
@RequiredArgsConstructor
public class SimpleFinController {

    private final SimpleFinSyncService syncService;
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

    /** Pull accounts/transactions now and run them through the ingest pipeline. */
    @PostMapping("/sync")
    public ImportRunDto sync() {
        try {
            return importRunMapper.toDto(syncService.sync());
        } catch (final IllegalStateException upstream) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, upstream.getMessage(), upstream);
        }
    }

}
