package ca.joaoborges.finance.merchant;

import ca.joaoborges.finance.common.MapStructConfig;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(config = MapStructConfig.class)
public interface MerchantMapper {

    MerchantDto toDto(Merchant merchant);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "logoUrl", ignore = true)
    Merchant toEntity(MerchantDto dto);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "logoUrl", ignore = true)
    void update(@MappingTarget Merchant merchant, MerchantDto dto);

}
