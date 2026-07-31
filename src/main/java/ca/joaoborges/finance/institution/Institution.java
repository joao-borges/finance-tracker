package ca.joaoborges.finance.institution;

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

/**
 * A grouping layer above accounts — the bank/provider. Its {@code offBudget}
 * toggle cascades to every account of the institution (the account-level flag
 * stays the source of truth for ingest).
 */
@Entity
@Table(name = "institutions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Institution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    private String website;

    @Column(name = "logo_url")
    private String logoUrl;

    @Builder.Default
    @Column(name = "off_budget", nullable = false)
    private boolean offBudget = false;

}
