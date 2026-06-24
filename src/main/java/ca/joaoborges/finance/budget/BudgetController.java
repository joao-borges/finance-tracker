package ca.joaoborges.finance.budget;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/budgets")
@RequiredArgsConstructor
public class BudgetController {

    private final BudgetService budgetService;
    private final BudgetPdfExporter budgetPdfExporter;

    @GetMapping("/{month}/summary")
    public BudgetSummary summary(@PathVariable final String month) {
        return budgetService.summary(month);
    }

    /** Bulk-set planned amounts for the month; returns the recomputed summary. */
    @PutMapping("/{month}")
    public BudgetSummary setPlanned(@PathVariable final String month, @RequestBody final List<BudgetEntry> entries) {
        return budgetService.setBudgets(month, entries);
    }

    @DeleteMapping("/{month}")
    public BudgetSummary clear(@PathVariable final String month) {
        return budgetService.clear(month);
    }

    @PostMapping("/{month}/copy-previous")
    public BudgetSummary copyPrevious(@PathVariable final String month) {
        return budgetService.copyFromPrevious(month);
    }

    @GetMapping("/{month}/export")
    public ResponseEntity<byte[]> export(@PathVariable final String month) {
        final byte[] pdf = budgetPdfExporter.export(month);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("budget-" + month + ".pdf").build().toString())
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

}

