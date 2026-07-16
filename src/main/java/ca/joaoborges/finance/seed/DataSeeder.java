package ca.joaoborges.finance.seed;

import ca.joaoborges.finance.category.Category;
import ca.joaoborges.finance.category.CategoryGroup;
import ca.joaoborges.finance.category.CategoryGroupRepository;
import ca.joaoborges.finance.category.CategoryRepository;
import ca.joaoborges.finance.common.FaviconService;
import ca.joaoborges.finance.merchant.Merchant;
import ca.joaoborges.finance.merchant.MerchantRepository;
import ca.joaoborges.finance.rule.Rule;
import ca.joaoborges.finance.rule.RuleRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Optionally seeds category groups, categories, merchants, and rules at
 * startup. The seed file is personal, so it does NOT ship with the repo: point
 * {@code finance.seed.file} (env {@code FINANCE_SEED_FILE}) at a JSON file, or
 * bake one into the classpath at {@code seed/seed-data.json}. With neither,
 * seeding is skipped. Idempotent: each group/category is matched by natural
 * key (group name, then category name within the group) and each rule by
 * name, so an existing row is never duplicated and user edits are left
 * untouched. Runs after Liquibase has built the schema.
 */
@Component
@Slf4j
public class DataSeeder implements ApplicationRunner {

    private static final String SEED_RESOURCE = "seed/seed-data.json";

    private final CategoryGroupRepository groupRepository;
    private final CategoryRepository categoryRepository;
    private final RuleRepository ruleRepository;
    private final MerchantRepository merchantRepository;
    private final FaviconService faviconService;
    private final ObjectMapper objectMapper;
    private final String seedFile;

    public DataSeeder(final CategoryGroupRepository groupRepository,
                      final CategoryRepository categoryRepository,
                      final RuleRepository ruleRepository,
                      final MerchantRepository merchantRepository,
                      final FaviconService faviconService,
                      final ObjectMapper objectMapper,
                      @Value("${finance.seed.file:}") final String seedFile) {
        this.groupRepository = groupRepository;
        this.categoryRepository = categoryRepository;
        this.ruleRepository = ruleRepository;
        this.merchantRepository = merchantRepository;
        this.faviconService = faviconService;
        this.objectMapper = objectMapper;
        this.seedFile = seedFile;
    }

    @Override
    @Transactional
    public void run(final ApplicationArguments args) {
        final SeedData seed = load();
        if (seed == null) {
            return;
        }
        final Map<String, Category> categoriesByName = seedCategories(seed);
        final Map<String, Merchant> merchantsByName = seedMerchants(seed);
        final int linkedRules = seedRules(seed, categoriesByName, merchantsByName);
        log.info("Seed complete: {} category group(s), {} categor(ies), {} merchant(s), {} rule(s) (re)linked",
                seed.groups().size(), categoriesByName.size(), merchantsByName.size(), linkedRules);
    }

    private SeedData load() {
        try {
            if (StringUtils.hasText(seedFile)) {
                final Path path = Path.of(seedFile);
                if (!Files.isReadable(path)) {
                    log.warn("Seed file {} is not readable — skipping seeding", seedFile);
                    return null;
                }
                try (InputStream in = Files.newInputStream(path)) {
                    return objectMapper.readValue(in, SeedData.class);
                }
            }
            final ClassPathResource resource = new ClassPathResource(SEED_RESOURCE);
            if (!resource.exists()) {
                log.info("No seed data (finance.seed.file unset, no classpath {}) — skipping seeding", SEED_RESOURCE);
                return null;
            }
            try (InputStream in = resource.getInputStream()) {
                return objectMapper.readValue(in, SeedData.class);
            }
        } catch (final IOException unreadable) {
            throw new UncheckedIOException("Could not read seed data", unreadable);
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
                                .hidden(seedCategory.hidden())
                                .build()));
                byName.put(category.getName(), category);
            }
        }
        return byName;
    }

    private Map<String, Merchant> seedMerchants(final SeedData seed) {
        final Map<String, Merchant> byName = new HashMap<>();
        if (seed.merchants() == null) {
            return byName;
        }
        for (final SeedData.SeedMerchant seedMerchant : seed.merchants()) {
            final Merchant merchant = merchantRepository.findByNameIgnoreCase(seedMerchant.name())
                    .orElseGet(() -> merchantRepository.save(Merchant.builder()
                            .name(seedMerchant.name())
                            .website(seedMerchant.website())
                            .logoUrl(faviconService.resolveLogoUrl(seedMerchant.website()))
                            .build()));
            byName.put(merchant.getName(), merchant);
        }
        return byName;
    }

    /**
     * Creates missing rules and ensures each rule's structured merchant link is
     * set — including rules already in the DB from an earlier seed run that
     * predates merchant seeding. Returns how many rules ended up linked.
     */
    private int seedRules(final SeedData seed, final Map<String, Category> categoriesByName,
                          final Map<String, Merchant> merchantsByName) {
        int linked = 0;
        for (final SeedData.SeedRule seedRule : seed.rules()) {
            final Merchant merchant = seedRule.merchantName() == null
                    ? null : merchantsByName.get(seedRule.merchantName());
            final Rule existing = ruleRepository.findFirstByName(seedRule.name()).orElse(null);
            if (existing != null) {
                if (merchant != null && existing.getMerchant() == null) {
                    existing.setMerchant(merchant);
                    ruleRepository.save(existing);
                }
                if (existing.getMerchant() != null) {
                    linked++;
                }
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
                    .merchant(merchant)
                    .autoApprove(seedRule.autoApprove())
                    .priority(seedRule.priority())
                    .enabled(true)
                    .build());
            if (merchant != null) {
                linked++;
            }
        }
        return linked;
    }

}
