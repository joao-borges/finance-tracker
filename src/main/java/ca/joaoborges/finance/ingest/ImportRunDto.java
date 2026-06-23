package ca.joaoborges.finance.ingest;

import ca.joaoborges.finance.common.SourceType;
import lombok.Builder;

import java.time.Instant;

@Builder
public record ImportRunDto(
        Long id,
        SourceType source,
        ImportStatus status,
        Instant startedAt,
        Instant finishedAt,
        String fileName,
        int newCount,
        int dedupCount,
        int errorCount,
        int accountCount) {
}
