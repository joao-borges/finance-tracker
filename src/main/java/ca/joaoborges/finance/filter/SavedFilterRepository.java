package ca.joaoborges.finance.filter;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

import java.util.List;

@RepositoryRestResource(exported = false)
public interface SavedFilterRepository extends JpaRepository<SavedFilter, Long> {

    List<SavedFilter> findAllByOrderByNameAsc();

}
