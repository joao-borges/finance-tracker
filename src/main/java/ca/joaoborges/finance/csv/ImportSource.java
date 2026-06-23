package ca.joaoborges.finance.csv;

import ca.joaoborges.finance.account.Account;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A saved CSV field mapping for one institution (e.g. "RBC chequing CSV").
 * Tells the importer how to read a bank's export into the shared pipeline.
 */
@Entity
@Table(name = "import_sources")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImportSource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(name = "date_column", nullable = false)
    private String dateColumn;

    @Column(name = "date_format", nullable = false)
    private String dateFormat;

    @Column(name = "amount_column")
    private String amountColumn;

    @Enumerated(EnumType.STRING)
    @Column(name = "amount_sign_convention", nullable = false)
    private AmountSignConvention amountSignConvention;

    @Column(name = "merchant_column", nullable = false)
    private String merchantColumn;

    @Column(name = "description_column")
    private String descriptionColumn;

    @Builder.Default
    @Column(name = "has_header", nullable = false)
    private boolean hasHeader = true;

}
