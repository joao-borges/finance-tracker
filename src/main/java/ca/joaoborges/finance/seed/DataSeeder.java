package ca.joaoborges.finance.seed;

import ca.joaoborges.finance.category.Category;
import ca.joaoborges.finance.category.CategoryGroup;
import ca.joaoborges.finance.category.CategoryGroupRepository;
import ca.joaoborges.finance.category.CategoryRepository;
import ca.joaoborges.finance.rule.Rule;
import ca.joaoborges.finance.rule.RuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Seeds the curated Monarch category groups, categories, and rules from
 * {@code seed/seed-data.json} at startup. Idempotent: each group/category is
 * matched by natural key (group name, then category name within the group) and
 * each rule by name, so an existing row is never duplicated and user edits are
 * left untouched. Runs after Liquibase has built the schema.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements ApplicationRunner {

    private static final String SEED_RESOURCE = "seed/seed-data.json";

    private final CategoryGroupRepository groupRepository;
    private final CategoryRepository categoryRepository;
    private final RuleRepository ruleRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void run(final ApplicationArguments args) {
        final SeedData seed = load();
        final Map<String, Category> categoriesByName = seedCategories(seed);
        final int newRules = seedRules(seed, categoriesByName);
        log.info("Seed complete: {} category group(s), {} categor(ies), {} new rule(s)",
                seed.groups().size(), categoriesByName.size(), newRules);
    }

    private SeedData load() {
        try (InputStream in = new ClassPathResource(SEED_RESOURCE).getInputStream()) {
            return objectMapper.readValue(in, SeedData.class);
        } catch (final IOException unreadable) {
            throw new UncheckedIOException("Could not read seed data " + SEED_RESOURCE, unreadable);
        }
    }

    private Map<String, Category> seedCategories(final SeedData seed) {
        final Map<String, Category> byName = new HashMap<>();
        for (final SeedData.SeedGroup seedGroup : seed.groups()) {
            final CategoryGroup group = groupRepository.findByName(seedGroup.name())
                    .orElseGet(() -> groupRepository.save(CategoryGroup.builder()
                            .name(seedGroup.name())
                            .sortOrder(seedGroup.sortOrder())
                            .build()));
            for (final SeedData.SeedCategory seedCategory : seedGroup.categories()) {
                final Category category = categoryRepository.findByGroupAndName(group, seedCategory.name())
                        .orElseGet(() -> categoryRepository.save(Category.builder()
                                .group(group)
                                .name(seedCategory.name())
                                .icon(seedCategory.icon())
                                .income(seedGroup.income())
                                .sortOrder(seedCategory.sortOrder())
                                .build()));
                byName.put(category.getName(), category);
            }
        }
        return byName;
    }

    private int seedRules(final SeedData seed, final Map<String, Category> categoriesByName) {
        int created = 0;
        for (final SeedData.SeedRule seedRule : seed.rules()) {
            if (ruleRepository.existsByName(seedRule.name())) {
                continue;
            }
            final Category category = categoriesByName.get(seedRule.categoryName());
            if (category == null) {
                log.warn("Skipping seed rule '{}': category '{}' not found", seedRule.name(), seedRule.categoryName());
                continue;
            }
            ruleRepository.save(Rule.builder()
                    .name(seedRule.name())
                    .merchantMatch(seedRule.merchantMatch())
                    .category(category)
                    .autoApprove(seedRule.autoApprove())
                    .priority(seedRule.priority())
                    .enabled(true)
                    .build());
            created++;
        }
        return created;
    }

}
