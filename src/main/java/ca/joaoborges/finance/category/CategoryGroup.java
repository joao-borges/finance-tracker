package ca.joaoborges.finance.category;

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
 * A grouping of categories (Home, Kid, Car, …) for the budget layout.
 */
@Entity
@Table(name = "category_groups")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE, region = CacheRegions.CATEGORY_GROUPS)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategoryGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Builder.Default
    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    private String icon;

    private String color;

}
