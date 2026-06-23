package ca.joaoborges.finance.category;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

import java.util.List;

@RepositoryRestResource(exported = false)
public interface CategoryGroupRepository extends JpaRepository<CategoryGroup, Long> {

    List<CategoryGroup> findAllByOrderBySortOrderAscNameAsc();

}
