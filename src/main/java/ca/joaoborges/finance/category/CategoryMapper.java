package ca.joaoborges.finance.category;

import ca.joaoborges.finance.common.MapStructConfig;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

/**
 * Entity ↔ DTO mapping for categories. The group relation is resolved by the
 * controller (repository lookup), so the mapper only flattens it for reads.
 */
@Mapper(config = MapStructConfig.class)
public interface CategoryMapper {

    @Mapping(target = "groupId", source = "group.id")
    @Mapping(target = "groupName", source = "group.name")
    CategoryDto toDto(Category category);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "group", ignore = true)
    Category toEntity(CategoryDto dto);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "group", ignore = true)
    void update(@MappingTarget Category category, CategoryDto dto);

}
