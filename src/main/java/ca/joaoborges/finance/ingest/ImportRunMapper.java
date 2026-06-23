package ca.joaoborges.finance.ingest;

import ca.joaoborges.finance.common.MapStructConfig;
import org.mapstruct.Mapper;

@Mapper(config = MapStructConfig.class)
public interface ImportRunMapper {

    ImportRunDto toDto(ImportRun run);

}
