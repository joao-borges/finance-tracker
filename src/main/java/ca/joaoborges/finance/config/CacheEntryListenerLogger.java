package ca.joaoborges.finance.config;

import lombok.extern.slf4j.Slf4j;

import javax.cache.event.CacheEntryCreatedListener;
import javax.cache.event.CacheEntryEvent;
import javax.cache.event.CacheEntryExpiredListener;
import javax.cache.event.CacheEntryRemovedListener;
import javax.cache.event.CacheEntryUpdatedListener;

/**
 * Debug-level visibility into second-level cache activity. Registered on each
 * region in {@link CacheConfiguration}; needs a public no-arg constructor so the
 * JCache {@code FactoryBuilder} can instantiate it.
 */
@Slf4j
public class CacheEntryListenerLogger
        implements CacheEntryCreatedListener<Object, Object>,
        CacheEntryUpdatedListener<Object, Object>,
        CacheEntryExpiredListener<Object, Object>,
        CacheEntryRemovedListener<Object, Object> {

    @Override
    public void onCreated(final Iterable<CacheEntryEvent<?, ?>> events) {
        log(events, "created");
    }

    @Override
    public void onUpdated(final Iterable<CacheEntryEvent<?, ?>> events) {
        log(events, "updated");
    }

    @Override
    public void onExpired(final Iterable<CacheEntryEvent<?, ?>> events) {
        log(events, "expired");
    }

    @Override
    public void onRemoved(final Iterable<CacheEntryEvent<?, ?>> events) {
        log(events, "removed");
    }

    private void log(final Iterable<CacheEntryEvent<?, ?>> events, final String action) {
        if (!log.isDebugEnabled()) {
            return;
        }
        for (final CacheEntryEvent<?, ?> event : events) {
            log.debug("L2 cache {} [{}] key={}", action, event.getSource().getName(), event.getKey());
        }
    }

}
