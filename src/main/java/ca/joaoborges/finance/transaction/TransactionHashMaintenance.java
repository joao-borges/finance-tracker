package ca.joaoborges.finance.transaction;

import ca.joaoborges.finance.common.ContentHashing;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Keeps {@code content_hash} / {@code statement_hash} consistent with their
 * current definition (account-ID-keyed). Runs at boot over all rows — a no-op
 * once everything matches — and after an account merge, when moved rows must be
 * re-keyed under the canonical account. Without this, renames and merges leave
 * hashes keyed to stale account identity and cross-source dedup silently stops
 * matching.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TransactionHashMaintenance implements ApplicationRunner {

    private final TransactionRepository transactionRepository;

    @Override
    @Transactional
    public void run(final ApplicationArguments args) {
        final int changed = rebuild(transactionRepository.findAll());
        if (changed > 0) {
            log.info("Rebuilt dedup hashes on {} transaction(s)", changed);
        }
    }

    /** Recompute both hashes for the given rows; saves and returns how many changed. */
    @Transactional
    public int rebuild(final Collection<Transaction> transactions) {
        final List<Transaction> changed = new ArrayList<>();
        for (final Transaction transaction : transactions) {
            final String accountKey = String.valueOf(transaction.getAccount().getId());
            final String statementText = StringUtils.hasText(transaction.getDescription())
                    ? transaction.getDescription()
                    : transaction.getMerchantName();
            final String contentHash = ContentHashing.of(
                    accountKey, transaction.getPostedAt(), transaction.getAmount(), transaction.getMerchantName());
            final String statementHash = ContentHashing.ofStatement(
                    accountKey, transaction.getPostedAt(), transaction.getAmount(), statementText);
            if (!contentHash.equals(transaction.getContentHash())
                    || !statementHash.equals(transaction.getStatementHash())) {
                transaction.setContentHash(contentHash);
                transaction.setStatementHash(statementHash);
                changed.add(transaction);
            }
        }
        transactionRepository.saveAll(changed);
        return changed.size();
    }

}
