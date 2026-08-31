package ca.joaoborges.finance.ingest;

import ca.joaoborges.finance.transaction.TransactionDto;
import ca.joaoborges.finance.transaction.TransactionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Import history: what each run brought in, and undoing a run outright. */
@RestController
@RequestMapping("/api/imports")
@RequiredArgsConstructor
public class ImportRunController {

    private final ImportRunRepository importRunRepository;
    private final ImportRunMapper importRunMapper;
    private final ImportRunService importRunService;
    private final TransactionMapper transactionMapper;

    /** How many rows an undo removed — the run's own counters exclude quarantined duplicates. */
    public record DeletedCount(int deleted) {
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<ImportRunDto> history() {
        return importRunRepository.findAllByOrderByStartedAtDesc().stream().map(importRunMapper::toDto).toList();
    }

    @GetMapping("/{id}/transactions")
    @Transactional(readOnly = true)
    public List<TransactionDto> transactions(@PathVariable final Long id) {
        return importRunService.transactionsOf(id).stream().map(transactionMapper::toDto).toList();
    }

    @DeleteMapping("/{id}")
    public DeletedCount delete(@PathVariable final Long id) {
        return new DeletedCount(importRunService.delete(id));
    }

}
