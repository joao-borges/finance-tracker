package ca.joaoborges.finance.rule;

import ca.joaoborges.finance.common.MapStructConfig;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

/**
 * Entity ↔ DTO mapping for rules. Category and merchant relations are resolved
 * by the controller; match bookkeeping ({@code matchCount}, {@code lastMatchedAt})
 * is server-managed.
 */
@Mapper(config = MapStructConfig.class)
public interface RuleMapper {

    @Mapping(target = "categoryId", source = "category.id")
    @Mapping(target = "categoryName", source = "category.name")
    @Mapping(target = "merchantId", source = "merchant.id")
    @Mapping(target = "merchantName", source = "merchant.name")
    RuleDto toDto(Rule rule);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "category", ignore = true)
    @Mapping(target = "merchant", ignore = true)
    @Mapping(target = "matchCount", ignore = true)
    @Mapping(target = "lastMatchedAt", ignore = true)
    Rule toEntity(RuleDto dto);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "category", ignore = true)
    @Mapping(target = "merchant", ignore = true)
    @Mapping(target = "matchCount", ignore = true)
    @Mapping(target = "lastMatchedAt", ignore = true)
    void update(@MappingTarget Rule rule, RuleDto dto);

}
