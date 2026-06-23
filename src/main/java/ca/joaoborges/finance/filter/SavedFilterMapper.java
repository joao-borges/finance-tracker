package ca.joaoborges.finance.filter;

import ca.joaoborges.finance.common.MapStructConfig;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(config = MapStructConfig.class)
public interface SavedFilterMapper {

    SavedFilterDto toDto(SavedFilter savedFilter);

    @Mapping(target = "id", ignore = true)
    SavedFilter toEntity(SavedFilterDto dto);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    void update(@MappingTarget SavedFilter savedFilter, SavedFilterDto dto);

}
