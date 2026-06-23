package ca.joaoborges.finance.budget;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

import java.util.List;

@RepositoryRestResource(exported = false)
public interface BudgetRepository extends JpaRepository<Budget, Long> {

    List<Budget> findByMonth(String month);

}
