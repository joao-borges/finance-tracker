package ca.joaoborges.finance.budget;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

import java.util.List;
import java.util.Optional;

@RepositoryRestResource(exported = false)
public interface BudgetRepository extends JpaRepository<Budget, Long> {

    List<Budget> findByMonth(String month);

    Optional<Budget> findByMonthAndCategoryId(String month, Long categoryId);

}
