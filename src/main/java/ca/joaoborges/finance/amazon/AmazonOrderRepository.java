package ca.joaoborges.finance.amazon;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

import java.util.List;
import java.util.Optional;

@RepositoryRestResource(exported = false)
public interface AmazonOrderRepository extends JpaRepository<AmazonOrder, Long> {

    Optional<AmazonOrder> findByOrderNumber(String orderNumber);

    List<AmazonOrder> findAllByOrderByOrderedOnDescIdDesc();

}
