package ca.joaoborges.finance.simplefin;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Where an incremental SimpleFIN sync starts: the furthest-behind account's
 * watermark rather than a fixed lookback.
 *
 * <p>A fixed window silently loses data. A bank connection can sit dead at the
 * bridge for longer than the window; when it comes back, the rows it was
 * holding are already older than the lookback, so they are never requested
 * again and the gap becomes permanent — which is exactly how a week of Amex
 * transactions went missing. Starting from where the data actually stops makes
 * a reconnect self-heal: a broken account's watermark never advanced, so the
 * next sync reaches back over the whole outage.
 *
 * <p>Pure so the clamping is testable without a bridge or a database.
 */
final class SyncWindow {

    /** Always re-request at least this much, so rows the bank posts late still land. */
    static final Duration MIN_OVERLAP = Duration.ofDays(2);

    /**
     * Ceiling on the window. Without it a permanently dead account (a closed card
     * the bridge still lists) would pin every sync to a full-history pull. Past
     * this, backfill with an explicit range import — or archive the account,
     * which drops it out of the watermark entirely.
     */
    static final Duration MAX_LOOKBACK = Duration.ofDays(90);

    /** Fallback when no account has a watermark yet (a first-ever sync). */
    static final Duration COLD_START_LOOKBACK = Duration.ofDays(7);

    private SyncWindow() {
    }

    static Instant startFrom(final Optional<Instant> watermark, final Instant now) {
        final Instant from = watermark.orElseGet(() -> now.minus(COLD_START_LOOKBACK));
        final Instant floor = now.minus(MAX_LOOKBACK);
        final Instant ceiling = now.minus(MIN_OVERLAP);
        if (from.isBefore(floor)) {
            return floor;
        }
        if (from.isAfter(ceiling)) {
            return ceiling;
        }
        return from;
    }

}
