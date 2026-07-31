package ca.joaoborges.finance.institution;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

import java.util.List;

@RepositoryRestResource(exported = false)
public interface InstitutionRepository extends JpaRepository<Institution, Long> {

    List<Institution> findAllByOrderByNameAsc();

}
