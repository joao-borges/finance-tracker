package ca.joaoborges.finance.transaction;

import ca.joaoborges.finance.common.ContentHashing;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Idempotent boot-time backfill of {@code statement_hash} for rows created
 * before the column existed. Uses the raw description when stored (SimpleFIN
 * rows since it started being persisted), else the merchant text — which for
 * CSV rows IS the raw statement text. No-op once every row has a hash.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StatementHashBackfill implements ApplicationRunner {

    private final TransactionRepository transactionRepository;

    @Override
    @Transactional
    public void run(final ApplicationArguments args) {
        final List<Transaction> missing = transactionRepository.findByStatementHashIsNull();
        if (missing.isEmpty()) {
            return;
        }
        for (final Transaction transaction : missing) {
            final String statementText = StringUtils.hasText(transaction.getDescription())
                    ? transaction.getDescription()
                    : transaction.getMerchantName();
            transaction.setStatementHash(ContentHashing.ofStatement(
                    transaction.getAccount().getName(),
                    transaction.getPostedAt(),
                    transaction.getAmount(),
                    statementText));
        }
        transactionRepository.saveAll(missing);
        log.info("Backfilled statement_hash on {} transaction(s)", missing.size());
    }

}
