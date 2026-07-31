package ca.joaoborges.finance.account;

import ca.joaoborges.finance.common.MapStructConfig;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

/**
 * Entity ↔ DTO mapping for accounts. Server-managed fields ({@code id},
 * {@code logoUrl}, sync metadata) are never taken from the request; the
 * controller derives {@code logoUrl} from the website.
 */
@Mapper(config = MapStructConfig.class)
public interface AccountMapper {

    @Mapping(target = "mergedIntoId", source = "mergedInto.id")
    @Mapping(target = "mergedIntoName", source = "mergedInto.name")
    @Mapping(target = "institutionId", source = "institution.id")
    @Mapping(target = "institutionName", source = "institution.name")
    AccountDto toDto(Account account);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "importRef", ignore = true)
    @Mapping(target = "mergedInto", ignore = true)
    @Mapping(target = "institution", ignore = true)
    @Mapping(target = "logoUrl", ignore = true)
    @Mapping(target = "balanceDate", ignore = true)
    @Mapping(target = "lastSyncedAt", ignore = true)
    Account toEntity(AccountDto dto);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "importRef", ignore = true)
    @Mapping(target = "mergedInto", ignore = true)
    @Mapping(target = "institution", ignore = true)
    @Mapping(target = "logoUrl", ignore = true)
    @Mapping(target = "balanceDate", ignore = true)
    @Mapping(target = "lastSyncedAt", ignore = true)
    void update(@MappingTarget Account account, AccountDto dto);

}
