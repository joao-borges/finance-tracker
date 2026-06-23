package ca.joaoborges.finance.ingest;

import ca.joaoborges.finance.common.SourceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * One ingestion run (a SimpleFIN sync or a CSV import), with the row counts it
 * produced. Surfaced in the import history.
 */
@Entity
@Table(name = "import_runs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImportRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SourceType source;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ImportStatus status;

    @Column(name = "file_name")
    private String fileName;

    @Builder.Default
    @Column(name = "new_count", nullable = false)
    private int newCount = 0;

    @Builder.Default
    @Column(name = "dedup_count", nullable = false)
    private int dedupCount = 0;

    @Builder.Default
    @Column(name = "error_count", nullable = false)
    private int errorCount = 0;

    @Builder.Default
    @Column(name = "account_count", nullable = false)
    private int accountCount = 0;

}
