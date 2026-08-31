package ca.joaoborges.finance.ingest;

import ca.joaoborges.finance.match.MatchingService;
import ca.joaoborges.finance.transaction.Transaction;
import ca.joaoborges.finance.transaction.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Read and undo side of an {@link ImportRun}: what a run brought in, and
 * removing a run that shouldn't have happened.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ImportRunService {

    private final ImportRunRepository importRunRepository;
    private final TransactionRepository transactionRepository;
    private final MatchingService matchingService;

    /** The rows a run produced, newest first. Split children fold into their parent. */
    @Transactional(readOnly = true)
    public List<Transaction> transactionsOf(final Long id) {
        return transactionRepository.findByImportRunAndSplitParentIsNullOrderByPostedAtDesc(require(id));
    }

    /**
     * Undo an import: delete every row it produced — quarantined duplicates and
     * split children included — then the run itself. Matches are detached first
     * so a transfer partner or a pending refund outside the run reverts cleanly.
     *
     * <p>Unlike deleting a single transaction this deliberately does NOT
     * tombstone the dedup keys. Dropping a whole run means the wrong file (or
     * the wrong date window) was imported, not that those transactions are
     * unwanted forever — a later deliberate re-import has to be able to bring
     * them back.
     */
    @Transactional
    public int delete(final Long id) {
        final ImportRun run = require(id);
        final List<Transaction> rows = transactionRepository.findByImportRun(run);
        for (final Transaction row : rows) {
            matchingService.detachForDeletion(row);
        }
        // Children first — they hold the FK to a split parent in the same run.
        transactionRepository.deleteAll(rows.stream().filter(row -> row.getSplitParent() != null).toList());
        transactionRepository.flush();
        transactionRepository.deleteAll(rows.stream().filter(row -> row.getSplitParent() == null).toList());
        transactionRepository.flush();
        importRunRepository.delete(run);
        log.info("Deleted import run {} ({}) and its {} transaction(s)", id, run.getFileName(), rows.size());
        return rows.size();
    }

    private ImportRun require(final Long id) {
        return importRunRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Import run not found"));
    }

}
