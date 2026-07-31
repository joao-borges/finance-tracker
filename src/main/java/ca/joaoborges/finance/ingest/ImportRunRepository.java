package ca.joaoborges.finance.ingest;

import ca.joaoborges.finance.common.SourceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

import java.util.Optional;

import java.util.List;

@RepositoryRestResource(exported = false)
public interface ImportRunRepository extends JpaRepository<ImportRun, Long> {

    /** Latest run for a source (the daily status digest). */
    Optional<ImportRun> findFirstBySourceOrderByStartedAtDesc(SourceType source);

    List<ImportRun> findAllByOrderByStartedAtDesc();

}
