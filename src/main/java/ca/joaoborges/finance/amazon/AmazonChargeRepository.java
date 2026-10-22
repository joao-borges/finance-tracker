package ca.joaoborges.finance.amazon;

import ca.joaoborges.finance.transaction.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

import java.util.Collection;
import java.util.List;

@RepositoryRestResource(exported = false)
public interface AmazonChargeRepository extends JpaRepository<AmazonCharge, Long> {

    /** Charges still waiting for a bank transaction (hand-unlinked ones excluded), oldest first. */
    List<AmazonCharge> findByTransactionIsNullAndManualFalseOrderByChargedOnAscIdAsc();

    /** Every linked charge — the matcher needs to know which transactions are spoken for. */
    List<AmazonCharge> findByTransactionIsNotNull();

    List<AmazonCharge> findByTransaction(Transaction transaction);

    /** Charges linked to any of these transactions, orders fetched for list summaries. */
    @Query("SELECT c FROM AmazonCharge c JOIN FETCH c.order WHERE c.transaction.id IN :ids")
    List<AmazonCharge> findByTransactionIdIn(@Param("ids") Collection<Long> ids);

}
