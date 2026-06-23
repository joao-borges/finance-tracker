package ca.joaoborges.finance.common;

/**
 * Hibernate second-level cache region names. Each cacheable entity references
 * one of these in its {@code @Cache(region = ...)}, and the matching JCache
 * region is created explicitly in {@code CacheConfiguration} — nothing is
 * cached unless it has a region here.
 */
public final class CacheRegions {

    public static final String ACCOUNTS = "accounts";
    public static final String MERCHANTS = "merchants";
    public static final String CATEGORIES = "categories";
    public static final String CATEGORY_GROUPS = "categoryGroups";
    public static final String RULES = "rules";

    private CacheRegions() {
    }

}
