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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

    /**
     * Try to auto-match a freshly imported transaction, else propose a suggestion.
     * Works in both directions: a new inflow searches for its purchase, and a new
     * outflow lets earlier-ingested unmatched inflows claim it — so payload order
     * (refund before purchase) doesn't lose matches. Same-account refund evidence
     * outranks a weak cross-account transfer coincidence; only a high-confidence
     * transfer preempts the refund check.
     */
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
                .filter(other -> !suggestionRepository.pairDismissed(tx, other))
                .min(transferPreference(tx));
        if (transfer.isPresent() && transferHighConfidence(tx, transfer.get())) {
            applyTransfer(tx, transfer.get());
            return;
        }

        if (tx.getAmount().signum() > 0) {
            final Optional<Transaction> purchase = bestRefundTarget(tx, candidates);
            if (purchase.isPresent()) {
                applyOrSuggestRefund(tx, purchase.get());
                return;
            }
        } else if (matchReverseRefunds(tx, candidates)) {
            return;
        }

        transfer.ifPresent(other -> suggestTransfer(tx, other));
    }

    /**
     * Reverse refund direction: a new outflow may be the purchase for inflows that
     * were ingested before it. Each such inflow re-picks its best target from the
     * pool including this purchase; it only binds here if this purchase wins.
     * Returns true when any inflow claimed this purchase.
     */
    private boolean matchReverseRefunds(final Transaction purchase, final List<Transaction> candidates) {
        boolean claimed = false;
        for (final Transaction inflow : candidates) {
            if (inflow.getAmount().signum() <= 0 || inflow.getMatchedWith() != null) {
                continue;
            }
            if (!isRefundShape(inflow, purchase)) {
                continue;
            }
            final List<Transaction> pool = new ArrayList<>(candidates);
            pool.removeIf(t -> t.getId().equals(inflow.getId()));
            pool.add(purchase);
            final Optional<Transaction> best = bestRefundTarget(inflow, pool);
            if (best.isPresent() && best.get().getId().equals(purchase.getId())) {
                applyOrSuggestRefund(inflow, purchase);
                claimed = true;
            }
        }
        return claimed;
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
        validateStillApplies(suggestion);
        // applyMatch -> link()/applyRefund() removes suggestions involving the
        // legs (incl. this one), so no separate delete here — doing both
        // double-deletes the row.
        applyMatch(suggestion.getLegA(), suggestion.getLegB(), suggestion.getType());
    }

    /** Suggestions can go stale (a leg matched elsewhere, a purchase refunded past this amount). */
    private void validateStillApplies(final MatchSuggestion suggestion) {
        final Transaction a = suggestion.getLegA();
        final Transaction b = suggestion.getLegB();
        if (suggestion.getType() == MatchType.TRANSFER) {
            if (a.getMatchedWith() != null || b.getMatchedWith() != null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "A leg of this suggestion is already matched — refresh the list");
            }
            return;
        }
        final Transaction refund = a.getAmount().signum() > 0 ? a : b;
        final Transaction purchase = refund == a ? b : a;
        if (refund.getMatchedWith() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This refund is already matched — refresh the list");
        }
        final BigDecimal remaining = remainingAfterApplied(purchase);
        if (remaining.signum() <= 0 || refund.getAmount().compareTo(remaining) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Refund exceeds the purchase's unrefunded remainder — refresh the list");
        }
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
        if (type == MatchType.REFUND) {
            final Transaction refund = a.getAmount().signum() > 0 ? a : b;
            final Transaction purchase = refund == a ? b : a;
            if (refund.getAmount().signum() <= 0 || purchase.getAmount().signum() >= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "A refund match needs one inflow and one outflow");
            }
            if (refund.getAmount().compareTo(remainingAfterApplied(purchase)) > 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Refund exceeds the purchase's unrefunded remainder");
            }
        }
        applyMatch(a, b, type);
        return a;
    }

    @Transactional
    public Transaction unmatch(final Long transactionId) {
        final Transaction tx = require(transactionId);
        final Transaction partner = tx.getMatchedWith();
        if (partner == null || tx.getMatchType() == null) {
            return tx;
        }
        // Transfers are 1:1 — unlink both legs. Refunds are many:1 onto a purchase
        // (which keeps matched_with null), so only the refund leg is unlinked.
        if (tx.getMatchType() == MatchType.TRANSFER) {
            revert(partner);
            transactionRepository.save(partner);
        }
        revert(tx);
        return transactionRepository.save(tx);
    }

    /**
     * Backfill: scan existing un-matched transactions and auto-match / suggest.
     * Legs are consumed only by actual applies — a mere suggestion never blocks a
     * transaction's other possibilities. Candidates arrive chronologically, so
     * earlier refunds reserve their purchases first (deterministic re-runs).
     */
    @Transactional
    public int scan() {
        final List<Transaction> all = transactionRepository
                .findMatchCandidates(Instant.EPOCH, Instant.now().plus(Duration.ofDays(1)));
        final Set<Long> consumed = new HashSet<>();
        int applied = 0;
        for (final Transaction tx : all) {
            if (consumed.contains(tx.getId()) || tx.getMatchedWith() != null) {
                continue;
            }
            final Optional<Transaction> transfer = all.stream()
                    .filter(other -> !other.getId().equals(tx.getId()) && !consumed.contains(other.getId()))
                    .filter(other -> isTransferCandidate(tx, other))
                    .filter(other -> !suggestionRepository.pairDismissed(tx, other))
                    .min(transferPreference(tx));
            if (transfer.isPresent() && transferHighConfidence(tx, transfer.get())) {
                applyTransfer(tx, transfer.get());
                consumed.add(tx.getId());
                consumed.add(transfer.get().getId());
                applied++;
                continue;
            }
            if (tx.getAmount().signum() > 0) {
                final Optional<Transaction> purchase = bestRefundTarget(tx, all.stream()
                        .filter(other -> !other.getId().equals(tx.getId()) && !consumed.contains(other.getId()))
                        .toList());
                if (purchase.isPresent()) {
                    if (applyOrSuggestRefund(tx, purchase.get())) {
                        // Consume only the refund — the purchase stays open for more refunds.
                        consumed.add(tx.getId());
                        applied++;
                    }
                    // Same-account refund evidence found — skip the weak-transfer fallback.
                    continue;
                }
            }
            transfer.ifPresent(other -> suggestTransfer(tx, other));
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

    /** Auto-apply when high-confidence, else suggest. Returns true when applied. */
    private boolean applyOrSuggestRefund(final Transaction refund, final Transaction purchase) {
        if (refundHighConfidence(refund, purchase)) {
            applyRefund(refund, purchase);
            return true;
        }
        suggestRefund(refund, purchase);
        return false;
    }

    private void applyRefund(final Transaction refund, final Transaction purchase) {
        // One-to-many: the refund points at the purchase; the purchase stays open
        // (matched_with null) so further refunds of the same purchase can match.
        refund.setMatchedWith(purchase);
        refund.setMatchType(MatchType.REFUND);
        refund.setNeedsReview(false);
        if (purchase.isAwaitingRefund()) {
            // Purchase is already out of budget via its flag; keep the refund out too.
            refund.setExcludedFromBudget(true);
        } else if (purchase.getCategory() != null) {
            refund.setCategory(purchase.getCategory());
        }
        // Only the refund is consumed — don't drop the purchase's other suggestions.
        suggestionRepository.deleteInvolving(refund);
        transactionRepository.save(refund);
        pruneOversubscribed(purchase);
    }

    /** After an apply, drop sibling suggestions that no longer fit the purchase's remainder. */
    private void pruneOversubscribed(final Transaction purchase) {
        final BigDecimal remaining = remainingAfterApplied(purchase);
        for (final MatchSuggestion sibling : suggestionRepository.findOpenByTypeInvolving(MatchType.REFUND, purchase)) {
            final Transaction siblingRefund = sibling.getLegA().getId().equals(purchase.getId())
                    ? sibling.getLegB() : sibling.getLegA();
            if (remaining.signum() <= 0 || siblingRefund.getAmount().compareTo(remaining) > 0) {
                suggestionRepository.delete(sibling);
            }
        }
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

    private void suggestTransfer(final Transaction a, final Transaction b) {
        if (suggestionRepository.existsPair(a, b)) {
            return;
        }
        suggestionRepository.save(MatchSuggestion.builder()
                .legA(a).legB(b).type(MatchType.TRANSFER).createdAt(Instant.now()).build());
    }

    /**
     * A refund keeps exactly one open suggestion — its current best target. A
     * better candidate replaces the old suggestion instead of piling up next to
     * it (purchases can still carry several, one per refund: one-to-many).
     */
    private void suggestRefund(final Transaction refund, final Transaction purchase) {
        boolean alreadySuggested = false;
        for (final MatchSuggestion existing : suggestionRepository.findOpenByTypeInvolving(MatchType.REFUND, refund)) {
            if (existing.getLegA().getId().equals(purchase.getId())
                    || existing.getLegB().getId().equals(purchase.getId())) {
                alreadySuggested = true;
            } else {
                suggestionRepository.delete(existing);
            }
        }
        if (alreadySuggested || suggestionRepository.existsPair(refund, purchase)) {
            return;
        }
        suggestionRepository.save(MatchSuggestion.builder()
                .legA(refund).legB(purchase).type(MatchType.REFUND).createdAt(Instant.now()).build());
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

    /** Structural refund-pair check: signs, account, merchant, window. No amount math. */
    private boolean isRefundShape(final Transaction refund, final Transaction purchase) {
        if (refund.getMatchedWith() != null || refund.getAmount().signum() <= 0) {
            return false;
        }
        // The purchase must be a plain outflow (not itself a refund/transfer leg).
        if (purchase.getMatchType() != null || purchase.getAmount().signum() >= 0) {
            return false;
        }
        if (!sameAccount(refund, purchase) || !sameMerchant(refund, purchase)) {
            return false;
        }
        return !purchase.getPostedAt().isAfter(refund.getPostedAt())
                && Duration.between(purchase.getPostedAt(), refund.getPostedAt()).compareTo(REFUND_WINDOW) <= 0;
    }

    /** Hard remainder: the purchase amount minus refunds already applied against it. */
    private BigDecimal remainingAfterApplied(final Transaction purchase) {
        return purchase.getAmount().negate().subtract(transactionRepository.sumRefundedAgainst(purchase));
    }

    /**
     * Soft remainder for choosing a suggestion target: also subtracts amounts held
     * by other refunds' open suggestions, so several refunds spread over plausible
     * purchases instead of all piling onto the largest one.
     */
    private BigDecimal remainingForSuggesting(final Transaction purchase, final Transaction refund) {
        return remainingAfterApplied(purchase)
                .subtract(suggestionRepository.sumOpenSuggestionsAgainst(purchase, refund));
    }

    /**
     * Pick the purchase this refund most plausibly belongs to: exact-remainder
     * match first, then exact-original-amount, then nearest preceding date, with
     * an id tiebreak so re-runs are deterministic. Candidates whose pairing the
     * user already dismissed are skipped entirely (so the runner-up can surface).
     */
    private Optional<Transaction> bestRefundTarget(final Transaction refund, final List<Transaction> candidates) {
        final Map<Long, BigDecimal> remainingById = new HashMap<>();
        final List<Transaction> eligible = new ArrayList<>();
        for (final Transaction purchase : candidates) {
            if (!isRefundShape(refund, purchase) || suggestionRepository.pairDismissed(refund, purchase)) {
                continue;
            }
            final BigDecimal remaining = remainingForSuggesting(purchase, refund);
            if (remaining.signum() > 0 && refund.getAmount().compareTo(remaining) <= 0) {
                remainingById.put(purchase.getId(), remaining);
                eligible.add(purchase);
            }
        }
        return eligible.stream().min(Comparator
                .comparing((Transaction p) -> refund.getAmount().compareTo(remainingById.get(p.getId())) == 0 ? 0 : 1)
                .thenComparing(p -> refund.getAmount().compareTo(p.getAmount().negate()) == 0 ? 0 : 1)
                .thenComparing(p -> Duration.between(p.getPostedAt(), refund.getPostedAt()))
                .thenComparing(Transaction::getId));
    }

    private boolean refundHighConfidence(final Transaction refund, final Transaction purchase) {
        final boolean exact = refund.getAmount().compareTo(purchase.getAmount().negate()) == 0;
        final boolean canonical = refund.getMerchant() != null && purchase.getMerchant() != null
                && refund.getMerchant().getId().equals(purchase.getMerchant().getId());
        return exact && canonical;
    }

    /** Nearest date wins; id tiebreak keeps re-runs deterministic. */
    private Comparator<Transaction> transferPreference(final Transaction tx) {
        return Comparator.comparing((Transaction other) -> dateGap(tx, other))
                .thenComparing(Transaction::getId);
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
