package ca.joaoborges.finance.category;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

import java.util.List;
import java.util.Optional;

@RepositoryRestResource(exported = false)
public interface CategoryRepository extends JpaRepository<Category, Long> {

    List<Category> findAllByOrderBySortOrderAscNameAsc();

    List<Category> findAllByOrderByNameAsc();

    Optional<Category> findByGroupAndName(CategoryGroup group, String name);

    Optional<Category> findFirstByNameIgnoreCase(String name);

}
