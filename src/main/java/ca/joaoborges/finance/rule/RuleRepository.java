package ca.joaoborges.finance.rule;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

import java.util.List;

@RepositoryRestResource(exported = false)
public interface RuleRepository extends JpaRepository<Rule, Long> {

    /** Enabled rules in evaluation order (lower priority first). */
    List<Rule> findByEnabledTrueOrderByPriorityAscIdAsc();

    List<Rule> findAllByOrderByPriorityAscIdAsc();

}
