package ca.joaoborges.finance.merchant;

import ca.joaoborges.finance.common.CacheRegions;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A canonical, deduplicated payee ("Amazon", "Tim Hortons"), distinct from the
 * raw bank descriptor a transaction stores. Transactions link to one via
 * {@code merchant_id}; rules can set that link. An optional {@code website}
 * resolves to a {@code logoUrl} (favicon) for easy identification.
 */
@Entity
@Table(name = "merchants")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE, region = CacheRegions.MERCHANTS)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Merchant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    /** Optional emoji shown when there's no website-derived logo. */
    private String icon;

    private String website;

    @Column(name = "logo_url")
    private String logoUrl;

}
