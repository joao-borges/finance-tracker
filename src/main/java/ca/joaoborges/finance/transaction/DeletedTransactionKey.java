package ca.joaoborges.finance.transaction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Tombstone for a deleted transaction. Both ingest paths silently skip these
 * keys — without them a deleted SimpleFIN row still inside the sync lookback
 * window would simply be re-imported on the next sync, and re-importing a CSV
 * would resurrect deleted rows.
 */
@Entity
@Table(name = "deleted_transaction_keys")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeletedTransactionKey {

    @Id
    @Column(name = "dedup_key")
    private String dedupKey;

    @Builder.Default
    @Column(name = "deleted_at", nullable = false)
    private Instant deletedAt = Instant.now();

}
