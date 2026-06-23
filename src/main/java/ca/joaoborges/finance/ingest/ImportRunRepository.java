package ca.joaoborges.finance.ingest;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

import java.util.List;

@RepositoryRestResource(exported = false)
public interface ImportRunRepository extends JpaRepository<ImportRun, Long> {

    List<ImportRun> findAllByOrderByStartedAtDesc();

}
