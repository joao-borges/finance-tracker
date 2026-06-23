package ca.joaoborges.finance.config;

import ca.joaoborges.finance.common.CacheRegions;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.ExpiryPolicyBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.ehcache.expiry.ExpiryPolicy;
import org.ehcache.jsr107.Eh107Configuration;
import org.hibernate.cache.jcache.ConfigSettings;
import org.springframework.boot.cache.autoconfigure.JCacheManagerCustomizer;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;

import javax.cache.CacheManager;
import javax.cache.configuration.CacheEntryListenerConfiguration;
import javax.cache.configuration.Configuration;
import javax.cache.configuration.FactoryBuilder;
import javax.cache.configuration.MutableCacheEntryListenerConfiguration;
import java.time.Duration;
import java.util.List;

/**
 * Hibernate second-level cache wiring. EhCache 3 (via JSR-107) backs an
 * in-memory cache; regions are created explicitly here for exactly the entities
 * annotated with {@code @Cache} — nothing is cached implicitly. The same JCache
 * {@link CacheManager} is handed to Hibernate via {@link HibernatePropertiesCustomizer}.
 */
@org.springframework.context.annotation.Configuration
@EnableCaching
public class CacheConfiguration {

    private static final int HEAP_ENTRIES = 1_000;
    private static final Duration TIME_TO_LIVE = Duration.ofHours(6);

    /** Only these regions exist; an entity referencing any other region fails fast at boot. */
    private static final List<String> SECOND_LEVEL_REGIONS = List.of(
            CacheRegions.ACCOUNTS,
            CacheRegions.MERCHANTS,
            CacheRegions.CATEGORIES,
            CacheRegions.CATEGORY_GROUPS,
            CacheRegions.RULES);

    private static final CacheEntryListenerConfiguration<Object, Object> ENTRY_LISTENER =
            new MutableCacheEntryListenerConfiguration<>(
                    FactoryBuilder.factoryOf(CacheEntryListenerLogger.class), null, false, false);

    @Bean
    public JCacheManagerCustomizer secondLevelCacheCustomizer() {
        return cacheManager -> {
            for (final String region : SECOND_LEVEL_REGIONS) {
                createRegion(cacheManager, region);
            }
        };
    }

    @Bean
    public HibernatePropertiesCustomizer hibernateCacheManagerCustomizer(final CacheManager cacheManager) {
        return properties -> properties.put(ConfigSettings.CACHE_MANAGER, cacheManager);
    }

    private void createRegion(final CacheManager cacheManager, final String region) {
        for (final String existing : cacheManager.getCacheNames()) {
            if (existing.equals(region)) {
                return;
            }
        }
        cacheManager.createCache(region, cacheConfiguration()).registerCacheEntryListener(ENTRY_LISTENER);
    }

    private Configuration<Object, Object> cacheConfiguration() {
        final ExpiryPolicy<Object, Object> expiry = ExpiryPolicyBuilder.timeToLiveExpiration(TIME_TO_LIVE);
        return Eh107Configuration.fromEhcacheCacheConfiguration(
                CacheConfigurationBuilder
                        .newCacheConfigurationBuilder(Object.class, Object.class, ResourcePoolsBuilder.heap(HEAP_ENTRIES))
                        .withExpiry(expiry)
                        .build());
    }

}
