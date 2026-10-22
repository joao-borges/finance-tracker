package ca.joaoborges.finance.amazon;

import ca.joaoborges.finance.account.Account;
import ca.joaoborges.finance.amazon.AmazonChargeMatcher.Candidate;
import ca.joaoborges.finance.amazon.AmazonChargeMatcher.ChargeKey;
import ca.joaoborges.finance.amazon.AmazonChargeMatcher.PendingCharge;
import ca.joaoborges.finance.transaction.Transaction;
import ca.joaoborges.finance.transaction.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Amazon order details from the browser extension: stores what each order
 * bought and the card charges its invoice lists, then links every charge to
 * the bank transaction it became (see {@link AmazonChargeMatcher}). That link
 * is what lets the transaction list say what a bare "Amazon" charge was, and
 * the split editor pre-fill one line per item.
 */
@Service
@RequiredArgsConstructor
public class AmazonService {

    /** How far either side of the strict match window a hand link may look. */
    private static final int MANUAL_SLACK_DAYS = 10;
    private static final int SUMMARY_TITLE_LENGTH = 48;
    private static final int SUMMARY_TITLES = 2;

    private final AmazonOrderRepository orderRepository;
    private final AmazonChargeRepository chargeRepository;
    private final TransactionRepository transactionRepository;

    /**
     * Upsert the scraped orders (keyed by order number), then match every
     * charge still waiting for its bank row. Existing charges are kept by key
     * so their links survive a re-import; charges an invoice no longer lists
     * are dropped.
     */
    @Transactional
    public AmazonImportResult importOrders(final AmazonImportRequest request) {
        if (request == null || !StringUtils.hasText(request.marketplace()) || request.orders() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A marketplace and a list of orders are required");
        }
        int newOrders = 0;
        int charges = 0;
        for (final AmazonImportRequest.Order payload : request.orders()) {
            if (!StringUtils.hasText(payload.orderNumber()) || payload.orderedOn() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Every order needs a number and a date");
            }
            final AmazonOrder order = orderRepository.findByOrderNumber(payload.orderNumber().trim())
                    .orElse(null);
            final AmazonOrder target = order != null ? order : AmazonOrder.builder()
                    .orderNumber(payload.orderNumber().trim())
                    .build();
            if (order == null) {
                newOrders++;
            }
            target.setMarketplace(request.marketplace().trim());
            target.setOrderedOn(payload.orderedOn());
            target.setTotal(payload.total());
            target.setCurrency(payload.currency());
            target.setImportedAt(Instant.now());
            replaceItems(target, payload.items() == null ? List.of() : payload.items());
            reconcileCharges(target, payload.charges() == null ? List.of() : payload.charges());
            charges += target.getCharges().size();
            orderRepository.save(target);
        }
        orderRepository.flush();

        final int matched = rematch();
        final int unmatched = chargeRepository.findByTransactionIsNullAndManualFalseOrderByChargedOnAscIdAsc().size();
        return AmazonImportResult.builder()
                .orders(request.orders().size())
                .newOrders(newOrders)
                .charges(charges)
                .matched(matched)
                .unmatched(unmatched)
                .build();
    }

    /** Link every unlinked, non-pinned charge it can. Returns how many were linked. */
    @Transactional
    public int rematch() {
        final List<AmazonCharge> pending = chargeRepository.findByTransactionIsNullAndManualFalseOrderByChargedOnAscIdAsc();
        if (pending.isEmpty()) {
            return 0;
        }
        final LocalDate first = pending.getFirst().getChargedOn();
        final LocalDate last = pending.stream().map(AmazonCharge::getChargedOn).max(Comparator.naturalOrder()).orElse(first);
        final List<Transaction> transactions = amazonOutflows(
                first.minusDays(AmazonChargeMatcher.DAYS_BEFORE), last.plusDays(AmazonChargeMatcher.DAYS_AFTER));

        final Map<Long, Set<ChargeKey>> linked = new HashMap<>();
        for (final AmazonCharge charge : chargeRepository.findByTransactionIsNotNull()) {
            linked.computeIfAbsent(charge.getTransaction().getId(), ignored -> new HashSet<>()).add(charge.key());
        }
        final Map<Long, Long> matches = AmazonChargeMatcher.match(
                pending.stream().map(charge -> new PendingCharge(charge.getId(), charge.key())).toList(),
                transactions.stream().map(AmazonService::candidate).toList(),
                linked);

        final Map<Long, Transaction> byId = transactions.stream()
                .collect(Collectors.toMap(Transaction::getId, Function.identity()));
        for (final AmazonCharge charge : pending) {
            final Long transactionId = matches.get(charge.getId());
            if (transactionId != null) {
                charge.setTransaction(byId.get(transactionId));
            }
        }
        return matches.size();
    }

    @Transactional(readOnly = true)
    public List<AmazonOrder> orders() {
        return orderRepository.findAllByOrderByOrderedOnDescIdDesc();
    }

    /**
     * The orders behind a transaction. A split child answers for its parent —
     * the parent is the bank row the charge is linked to.
     */
    @Transactional(readOnly = true)
    public List<AmazonOrder> ordersFor(final Long transactionId) {
        final Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found"));
        final Transaction charged = transaction.getSplitParent() != null ? transaction.getSplitParent() : transaction;
        return chargeRepository.findByTransaction(charged).stream()
                .map(AmazonCharge::getOrder)
                .distinct()
                .toList();
    }

    /**
     * One-line "what was this" per transaction id — the first item titles of
     * the linked orders — for the transaction list. Rows with no Amazon detail
     * are absent from the map.
     */
    @Transactional(readOnly = true)
    public Map<Long, String> summaries(final Collection<Transaction> transactions) {
        final Map<Long, Long> chargedRow = new HashMap<>();
        for (final Transaction transaction : transactions) {
            final Transaction charged = transaction.getSplitParent() != null ? transaction.getSplitParent() : transaction;
            chargedRow.put(transaction.getId(), charged.getId());
        }
        if (chargedRow.isEmpty()) {
            return Map.of();
        }
        final Map<Long, Set<AmazonOrder>> ordersByRow = new HashMap<>();
        for (final AmazonCharge charge : chargeRepository.findByTransactionIdIn(new HashSet<>(chargedRow.values()))) {
            ordersByRow.computeIfAbsent(charge.getTransaction().getId(), ignored -> new LinkedHashSet<>()).add(charge.getOrder());
        }
        final Map<Long, String> summaries = new HashMap<>();
        chargedRow.forEach((transactionId, rowId) -> {
            final Set<AmazonOrder> orders = ordersByRow.get(rowId);
            if (orders != null) {
                summaries.put(transactionId, summarize(orders));
            }
        });
        return summaries;
    }

    /** Pin a charge to a transaction by hand; re-matching leaves it alone from then on. */
    @Transactional
    public AmazonOrder link(final Long chargeId, final Long transactionId) {
        final AmazonCharge charge = charge(chargeId);
        final Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown transaction " + transactionId));
        charge.setTransaction(transaction.getSplitParent() != null ? transaction.getSplitParent() : transaction);
        charge.setManual(true);
        return charge.getOrder();
    }

    /** Detach a charge and pin it unlinked, so the matcher doesn't put it straight back. */
    @Transactional
    public AmazonOrder unlink(final Long chargeId) {
        final AmazonCharge charge = charge(chargeId);
        charge.setTransaction(null);
        charge.setManual(true);
        return charge.getOrder();
    }

    /**
     * Transactions a charge could be linked to by hand: Amazon-looking
     * outflows in a looser window than the matcher's, closest amount first.
     */
    @Transactional(readOnly = true)
    public List<Transaction> candidates(final Long chargeId) {
        final AmazonCharge charge = charge(chargeId);
        final BigDecimal target = charge.getAmount().negate();
        return amazonOutflows(
                charge.getChargedOn().minusDays(AmazonChargeMatcher.DAYS_BEFORE + MANUAL_SLACK_DAYS),
                charge.getChargedOn().plusDays(AmazonChargeMatcher.DAYS_AFTER + MANUAL_SLACK_DAYS)).stream()
                .sorted(Comparator
                        .comparing((Transaction transaction) -> transaction.getAmount().subtract(target).abs())
                        .thenComparing(transaction -> Math.abs(transaction.getPostedAt().getEpochSecond()
                                - charge.getChargedOn().atStartOfDay(ZoneOffset.UTC).toEpochSecond()))
                        .thenComparing(Transaction::getId))
                .toList();
    }

    private AmazonCharge charge(final Long chargeId) {
        return chargeRepository.findById(chargeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Charge not found"));
    }

    private List<Transaction> amazonOutflows(final LocalDate from, final LocalDate to) {
        return transactionRepository.findOutflowsBetween(
                        from.atStartOfDay(ZoneOffset.UTC).toInstant(),
                        to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()).stream()
                .filter(transaction -> AmazonChargeMatcher.isAmazon(transaction.getMerchantName(),
                        transaction.getMerchant() == null ? null : transaction.getMerchant().getName()))
                .toList();
    }

    private static Candidate candidate(final Transaction transaction) {
        final Account account = transaction.getAccount();
        final String accountLabel = Stream.of(account.getName(), account.getImportRef(), account.getSimplefinName())
                .filter(Objects::nonNull)
                .collect(Collectors.joining(" "));
        return new Candidate(transaction.getId(), transaction.getPostedAt().atZone(ZoneOffset.UTC).toLocalDate(),
                transaction.getAmount(), accountLabel);
    }

    private static void replaceItems(final AmazonOrder order, final List<AmazonImportRequest.Item> items) {
        order.getItems().clear();
        int position = 0;
        for (final AmazonImportRequest.Item item : items) {
            if (!StringUtils.hasText(item.title())) {
                continue;
            }
            order.getItems().add(AmazonOrderItem.builder()
                    .order(order)
                    .position(position++)
                    .title(item.title().trim())
                    .quantity(item.quantity() == null || item.quantity() < 1 ? 1 : item.quantity())
                    .unitPrice(item.unitPrice())
                    .build());
        }
    }

    private static void reconcileCharges(final AmazonOrder order, final List<AmazonImportRequest.Charge> charges) {
        final Map<ChargeKey, AmazonCharge> existing = new LinkedHashMap<>();
        for (final AmazonCharge charge : order.getCharges()) {
            existing.put(charge.key(), charge);
        }
        final List<AmazonCharge> kept = new ArrayList<>();
        for (final AmazonImportRequest.Charge payload : charges) {
            if (payload.chargedOn() == null || payload.amount() == null || payload.amount().signum() <= 0) {
                continue;
            }
            final ChargeKey key = new ChargeKey(payload.chargedOn(), payload.amount(), payload.cardLast4());
            final AmazonCharge charge = existing.remove(key);
            if (charge != null) {
                charge.setCardLabel(payload.cardLabel());
                kept.add(charge);
            } else if (kept.stream().noneMatch(other -> other.key().equals(key))) {
                kept.add(AmazonCharge.builder()
                        .order(order)
                        .chargedOn(payload.chargedOn())
                        .amount(payload.amount())
                        .cardLabel(payload.cardLabel())
                        .cardLast4(payload.cardLast4())
                        .build());
            }
        }
        // Keep surviving instances in place (orphan removal deletes only the dropped ones).
        order.getCharges().retainAll(kept);
        kept.stream().filter(charge -> charge.getId() == null).forEach(order.getCharges()::add);
    }

    private static String summarize(final Set<AmazonOrder> orders) {
        final List<String> titles = orders.stream()
                .flatMap(order -> order.getItems().stream())
                .map(AmazonOrderItem::getTitle)
                .toList();
        if (titles.isEmpty()) {
            return orders.stream().map(AmazonOrder::getOrderNumber).collect(Collectors.joining(", "));
        }
        final String shown = titles.stream()
                .limit(SUMMARY_TITLES)
                .map(AmazonService::shorten)
                .collect(Collectors.joining("; "));
        return titles.size() > SUMMARY_TITLES ? shown + " (+" + (titles.size() - SUMMARY_TITLES) + " more)" : shown;
    }

    private static String shorten(final String title) {
        return title.length() <= SUMMARY_TITLE_LENGTH ? title : title.substring(0, SUMMARY_TITLE_LENGTH - 1).trim() + "…";
    }

}
