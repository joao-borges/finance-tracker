package ca.joaoborges.finance.transaction;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

import java.util.List;

@RepositoryRestResource(exported = false)
public interface DeletedTransactionKeyRepository extends JpaRepository<DeletedTransactionKey, String> {

    /** All tombstoned keys, snapshotted by the ingest paths to skip deleted rows. */
    @Query("SELECT k.dedupKey FROM DeletedTransactionKey k")
    List<String> findAllKeys();

}
