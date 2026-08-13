package ca.joaoborges.finance.filter;

import ca.joaoborges.finance.common.LongListConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * A named, reusable transactions filter, shared across the household. Criteria
 * are stored as plain id lists (comma-separated, no FKs) so a saved filter
 * survives deletion of the referenced account/merchant/category.
 */
@Entity
@Table(name = "saved_filters")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SavedFilter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(name = "from_date")
    private LocalDate fromDate;

    @Column(name = "to_date")
    private LocalDate toDate;

    @Builder.Default
    @Convert(converter = LongListConverter.class)
    @Column(name = "account_ids")
    private List<Long> accountIds = new ArrayList<>();

    @Builder.Default
    @Convert(converter = LongListConverter.class)
    @Column(name = "merchant_ids")
    private List<Long> merchantIds = new ArrayList<>();

    @Builder.Default
    @Convert(converter = LongListConverter.class)
    @Column(name = "category_ids")
    private List<Long> categoryIds = new ArrayList<>();

    @Builder.Default
    @Convert(converter = ca.joaoborges.finance.common.StringListConverter.class)
    private List<String> tags = new ArrayList<>();

    private Boolean review;

}
