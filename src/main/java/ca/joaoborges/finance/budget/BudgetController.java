package ca.joaoborges.finance.budget;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

    @GetMapping("/{month}/summary")
    public BudgetSummary summary(@PathVariable final String month) {
        return budgetService.summary(month);
    }

    /** Bulk-set planned amounts for the month; returns the recomputed summary. */
    @PutMapping("/{month}")
    public BudgetSummary setPlanned(@PathVariable final String month, @RequestBody final List<BudgetEntry> entries) {
        return budgetService.setBudgets(month, entries);
    }

}
