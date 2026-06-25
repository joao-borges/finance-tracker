package ca.joaoborges.finance.seed;

import java.util.List;

/**
 * Shape of {@code seed/seed-data.json} — the category groups, categories, and
 * rules exported from Monarch (parsed from the saved page source) that
 * {@link DataSeeder} ensures exist at startup.
 */
public record SeedData(List<SeedGroup> groups, List<SeedRule> rules, List<SeedMerchant> merchants) {

    public record SeedGroup(String name, int sortOrder, boolean income, List<SeedCategory> categories) {
    }

    public record SeedCategory(String name, String icon, int sortOrder) {
    }

    public record SeedRule(String name, String merchantMatch, String categoryName, int priority,
                           boolean autoApprove, String merchantName) {
    }

    public record SeedMerchant(String name, String website) {
    }

}
