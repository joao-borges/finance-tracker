package ca.joaoborges.finance.simplefin;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

import java.util.Optional;

@RepositoryRestResource(exported = false)
public interface SimpleFinConnectionRepository extends JpaRepository<SimpleFinConnection, Long> {

    Optional<SimpleFinConnection> findFirstByOrderByIdAsc();

}
