package ca.joaoborges.finance.category;

import ca.joaoborges.finance.common.MapStructConfig;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(config = MapStructConfig.class)
public interface CategoryGroupMapper {

    CategoryGroupDto toDto(CategoryGroup group);

    @Mapping(target = "id", ignore = true)
    CategoryGroup toEntity(CategoryGroupDto dto);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    void update(@MappingTarget CategoryGroup group, CategoryGroupDto dto);

}
