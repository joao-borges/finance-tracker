package ca.joaoborges.finance.match;

import ca.joaoborges.finance.category.Category;
import ca.joaoborges.finance.category.CategoryRepository;
import ca.joaoborges.finance.transaction.Transaction;
import ca.joaoborges.finance.transaction.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Pairs transactions into transfers (incl. credit-card payments) and refunds.
 *
 * <p>Transfer = opposite-signed, equal-amount legs on two different accounts
 * within a few days. The category follows where the +cash lands: a credit-card
 * inflow → "Credit Card Payment", otherwise "Transfer"; both legs are excluded
 * from budget. Refund = an inflow on the same account/merchant offsetting an
 * earlier purchase (partials allowed); the refund inherits the purchase's
 * category so it nets the spend — unless the purchase was flagged
 * {@code awaiting_refund}, in which case both legs are excluded.
 *
 * <p>High-confidence pairs are applied automatically; weaker ones become
 * {@link MatchSuggestion}s for review. See DESIGN.md "Matching".
 */
@Service
@RequiredArgsConstructor
public class MatchingService {

    private static final Duration TRANSFER_WINDOW = Duration.ofDays(5);
    private static final Duration REFUND_WINDOW = Duration.ofDays(90);
    private static final String[] PAYMENT_KEYWORDS = {
            "payment", "transfer", "e-transfer", "e transfer", "pymt", "online banking", "bill pay",
    };

    private final TransactionRepository transactionRepository;
    private final MatchSuggestionRepository suggestionRepository;
    private final CategoryRepository categoryRepository;

    // ---- ingest entry point -------------------------------------------------

    /** Try to auto-match a freshly imported transaction, else propose a suggestion. */
    public void matchNewTransaction(final Transaction tx) {
        if (tx.isDedup() || tx.isSplit() || tx.getMatchedWith() != null) {
            return;
        }
        final Instant from = tx.getPostedAt().minus(REFUND_WINDOW);
        final Instant to = tx.getPostedAt().plus(TRANSFER_WINDOW).plus(Duration.ofDays(1));
        final List<Transaction> candidates = transactionRepository.findMatchCandidates(from, to).stream()
                .filter(other -> !other.getId().equals(tx.getId()))
                .toList();

        final Optional<Transaction> transfer = candidates.stream()
                .filter(other -> isTransferCandidate(tx, other))
                .min(Comparator.comparing(other -> dateGap(tx, other)));
        if (transfer.isPresent()) {
            final Transaction other = transfer.get();
            if (transferHighConfidence(tx, other)) {
                applyTransfer(tx, other);
            } else {
                suggest(tx, other, MatchType.TRANSFER);
            }
            return;
        }

        if (tx.getAmount().signum() > 0) {
            final Optional<Transaction> purchase = candidates.stream()
                    .filter(other -> isRefundCandidate(tx, other))
                    .min(refundPreference(tx));
            purchase.ifPresent(p -> {
                if (refundHighConfidence(tx, p)) {
                    applyRefund(tx, p);
                } else {
                    suggest(tx, p, MatchType.REFUND);
                }
            });
        }
    }

    // ---- review-surface actions --------------------------------------------

    @Transactional(readOnly = true)
    public List<MatchSuggestion> suggestions() {
        return suggestionRepository.findByDismissedFalseOrderByCreatedAtDesc();
    }

    @Transactional
    public void confirm(final Long suggestionId) {
        final MatchSuggestion suggestion = suggestionRepository.findById(suggestionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Suggestion not found"));
        // applyMatch -> link() removes suggestions involving either leg (incl. this
        // one), so no separate delete here — doing both double-deletes the row.
        applyMatch(suggestion.getLegA(), suggestion.getLegB(), suggestion.getType());
    }

    @Transactional
    public void dismiss(final Long suggestionId) {
        final MatchSuggestion suggestion = suggestionRepository.findById(suggestionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Suggestion not found"));
        suggestion.setDismissed(true);
        suggestionRepository.save(suggestion);
    }

    @Transactional
    public Transaction manualMatch(final Long aId, final Long bId, final MatchType type) {
        if (aId == null || bId == null || aId.equals(bId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Two different transactions are required");
        }
        final Transaction a = require(aId);
        final Transaction b = require(bId);
        if (a.getMatchedWith() != null || b.getMatchedWith() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "One of the transactions is already matched");
        }
        applyMatch(a, b, type);
        return a;
    }

    @Transactional
    public Transaction unmatch(final Long transactionId) {
        final Transaction tx = require(transactionId);
        final Transaction partner = tx.getMatchedWith();
        if (partner == null) {
            return tx;
        }
        revert(tx);
        revert(partner);
        transactionRepository.save(partner);
        return transactionRepository.save(tx);
    }

    /** Backfill: scan existing un-matched transactions and auto-match / suggest. */
    @Transactional
    public int scan() {
        final List<Transaction> all = transactionRepository
                .findMatchCandidates(Instant.EPOCH, Instant.now().plus(Duration.ofDays(1)));
        final Set<Long> consumed = new HashSet<>();
        int applied = 0;
        for (final Transaction tx : all) {
            if (consumed.contains(tx.getId())) {
                continue;
            }
            final Optional<Transaction> transfer = all.stream()
                    .filter(other -> !other.getId().equals(tx.getId()) && !consumed.contains(other.getId()))
                    .filter(other -> isTransferCandidate(tx, other))
                    .min(Comparator.comparing(other -> dateGap(tx, other)));
            if (transfer.isPresent()) {
                final Transaction other = transfer.get();
                if (transferHighConfidence(tx, other)) {
                    applyTransfer(tx, other);
                    applied++;
                } else {
                    suggest(tx, other, MatchType.TRANSFER);
                }
                consumed.add(tx.getId());
                consumed.add(other.getId());
                continue;
            }
            if (tx.getAmount().signum() > 0) {
                final Optional<Transaction> purchase = all.stream()
                        .filter(other -> !other.getId().equals(tx.getId()) && !consumed.contains(other.getId()))
                        .filter(other -> isRefundCandidate(tx, other))
                        .min(refundPreference(tx));
                if (purchase.isPresent()) {
                    final Transaction p = purchase.get();
                    if (refundHighConfidence(tx, p)) {
                        applyRefund(tx, p);
                        applied++;
                    } else {
                        suggest(tx, p, MatchType.REFUND);
                    }
                    consumed.add(tx.getId());
                    consumed.add(p.getId());
                }
            }
        }
        return applied;
    }

    // ---- apply / revert -----------------------------------------------------

    private void applyMatch(final Transaction a, final Transaction b, final MatchType type) {
        if (type == MatchType.TRANSFER) {
            applyTransfer(a, b);
        } else {
            final Transaction refund = a.getAmount().signum() > 0 ? a : b;
            final Transaction purchase = a.getAmount().signum() > 0 ? b : a;
            applyRefund(refund, purchase);
        }
    }

    private void applyTransfer(final Transaction a, final Transaction b) {
        final Transaction inflow = a.getAmount().signum() > 0 ? a : b;
        final boolean toCreditCard = inflow.getAccount() != null
                && inflow.getAccount().getType() == ca.joaoborges.finance.account.AccountType.CREDIT_CARD;
        final Category category = categoryRepository
                .findFirstByNameIgnoreCase(toCreditCard ? "Credit Card Payment" : "Transfer")
                .orElse(null);
        for (final Transaction leg : List.of(a, b)) {
            leg.setExcludedFromBudget(true);
            leg.setNeedsReview(false);
            if (category != null) {
                leg.setCategory(category);
            }
        }
        link(a, b, MatchType.TRANSFER);
    }

    private void applyRefund(final Transaction refund, final Transaction purchase) {
        if (purchase.isAwaitingRefund()) {
            refund.setExcludedFromBudget(true);
            purchase.setExcludedFromBudget(true);
            purchase.setAwaitingRefund(false);
        } else if (purchase.getCategory() != null) {
            refund.setCategory(purchase.getCategory());
        }
        refund.setNeedsReview(false);
        link(refund, purchase, MatchType.REFUND);
    }

    private void link(final Transaction a, final Transaction b, final MatchType type) {
        a.setMatchedWith(b);
        a.setMatchType(type);
        b.setMatchedWith(a);
        b.setMatchType(type);
        suggestionRepository.deleteInvolving(a);
        suggestionRepository.deleteInvolving(b);
        transactionRepository.save(a);
        transactionRepository.save(b);
    }

    private void revert(final Transaction tx) {
        tx.setMatchedWith(null);
        tx.setMatchType(null);
        tx.setExcludedFromBudget(false);
        tx.setNeedsReview(true);
    }

    private void suggest(final Transaction a, final Transaction b, final MatchType type) {
        if (suggestionRepository.existsInvolving(a, b)) {
            return;
        }
        suggestionRepository.save(MatchSuggestion.builder()
                .legA(a).legB(b).type(type).createdAt(Instant.now()).build());
    }

    // ---- heuristics ---------------------------------------------------------

    private boolean isTransferCandidate(final Transaction tx, final Transaction other) {
        return other.getMatchedWith() == null
                && tx.getAccount() != null && other.getAccount() != null
                && !tx.getAccount().getId().equals(other.getAccount().getId())
                && tx.getAmount().compareTo(other.getAmount().negate()) == 0
                && dateGap(tx, other).compareTo(TRANSFER_WINDOW) <= 0;
    }

    private boolean transferHighConfidence(final Transaction a, final Transaction b) {
        final Transaction inflow = a.getAmount().signum() > 0 ? a : b;
        final boolean toCreditCard = inflow.getAccount() != null
                && inflow.getAccount().getType() == ca.joaoborges.finance.account.AccountType.CREDIT_CARD;
        return toCreditCard || hasPaymentKeyword(a) || hasPaymentKeyword(b);
    }

    private boolean isRefundCandidate(final Transaction refund, final Transaction purchase) {
        return purchase.getMatchedWith() == null
                && purchase.getAmount().signum() < 0
                && refund.getAmount().signum() > 0
                && sameAccount(refund, purchase)
                && sameMerchant(refund, purchase)
                && refund.getAmount().compareTo(purchase.getAmount().negate()) <= 0
                && !purchase.getPostedAt().isAfter(refund.getPostedAt())
                && Duration.between(purchase.getPostedAt(), refund.getPostedAt()).compareTo(REFUND_WINDOW) <= 0;
    }

    private boolean refundHighConfidence(final Transaction refund, final Transaction purchase) {
        final boolean exact = refund.getAmount().compareTo(purchase.getAmount().negate()) == 0;
        final boolean canonical = refund.getMerchant() != null && purchase.getMerchant() != null
                && refund.getMerchant().getId().equals(purchase.getMerchant().getId());
        return exact && canonical;
    }

    /** Prefer exact-amount, then nearest date, when several purchases could match a refund. */
    private Comparator<Transaction> refundPreference(final Transaction refund) {
        return Comparator
                .comparing((Transaction p) -> refund.getAmount().compareTo(p.getAmount().negate()) == 0 ? 0 : 1)
                .thenComparing(p -> Duration.between(p.getPostedAt(), refund.getPostedAt()));
    }

    private boolean sameAccount(final Transaction a, final Transaction b) {
        return a.getAccount() != null && b.getAccount() != null
                && a.getAccount().getId().equals(b.getAccount().getId());
    }

    private boolean sameMerchant(final Transaction a, final Transaction b) {
        if (a.getMerchant() != null && b.getMerchant() != null) {
            return a.getMerchant().getId().equals(b.getMerchant().getId());
        }
        return normalize(a.getMerchantName()).equals(normalize(b.getMerchantName()));
    }

    private boolean hasPaymentKeyword(final Transaction tx) {
        final String descriptor = normalize(tx.getMerchantName());
        for (final String keyword : PAYMENT_KEYWORDS) {
            if (descriptor.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private Duration dateGap(final Transaction a, final Transaction b) {
        return Duration.between(a.getPostedAt(), b.getPostedAt()).abs();
    }

    private String normalize(final String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private Transaction require(final Long id) {
        return transactionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found"));
    }

}
