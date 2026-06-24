package ca.joaoborges.finance.simplefin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * Single-row store for the SimpleFIN access URL (claimed from a setup token) and
 * the last successful sync time. The access URL embeds credentials — a secret
 * kept only in the DB.
 */
@Entity
@Table(name = "simplefin_connection")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SimpleFinConnection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "access_url", nullable = false)
    private String accessUrl;

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;

}
