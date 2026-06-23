package ca.joaoborges.finance.common;

import org.mapstruct.MapperConfig;
import org.mapstruct.ReportingPolicy;

/**
 * Shared MapStruct configuration: generate Spring components and ignore unmapped
 * target properties (DTOs carry read-only/derived fields the entities don't).
 * Referenced by every mapper via {@code @Mapper(config = MapStructConfig.class)}.
 */
@MapperConfig(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface MapStructConfig {
}
