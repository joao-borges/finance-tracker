package ca.joaoborges.finance.account;

import ca.joaoborges.finance.transaction.Transaction;
import ca.joaoborges.finance.transaction.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies the off-budget account state backwards: every existing transaction
 * of the account leaves the budget, is marked reviewed, and loses its category
 * — matched transfer legs keep their pairing and category (they are already
 * excluded and the category documents the transfer). Used by the account
 * toggle and the institution-level cascade.
 */
@Service
@RequiredArgsConstructor
public class OffBudgetService {

    private final TransactionRepository transactionRepository;

    @Transactional
    public void applyBackwards(final Account account) {
        for (final Transaction transaction : transactionRepository.findByAccount(account)) {
            transaction.setExcludedFromBudget(true);
            transaction.setNeedsReview(false);
            if (transaction.getMatchType() == null) {
                transaction.setCategory(null);
            }
            transactionRepository.save(transaction);
        }
    }

}
