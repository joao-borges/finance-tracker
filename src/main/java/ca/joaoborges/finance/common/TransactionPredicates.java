package ca.joaoborges.finance.common;

/**
 * The flag-semantics table from {@code PLAN.md}, expressed once as reusable JPQL
 * WHERE fragments. Every transaction list and every budget sum MUST build on
 * these — never re-derive the flag logic with ad-hoc conditions.
 *
 * <p>Fragments assume the {@code Transaction} alias {@code t}.
 *
 * <pre>
 * | flag                       | in list? | in budget? |
 * |----------------------------|----------|------------|
 * | normal                     |    ✅    |     ✅     |
 * | is_split (parent)          |    ❌    |     ❌     |
 * | split_parent_id != null    |    ✅    |     ✅     |
 * | excluded_from_budget       |    ✅    |     ❌     |
 * | is_dedup                   |    ❌    |     ❌     |
 * | needs_review               |    ✅    |  ✅ if categorized |
 * </pre>
 */
public final class TransactionPredicates {

    /** Default transaction list: hide split parents and quarantined duplicates. */
    public static final String VISIBLE_IN_LIST = "t.split = false AND t.dedup = false";

    /** Budget "actual spent": visible rows that are not excluded from budget math. */
    public static final String COUNTS_IN_BUDGET = VISIBLE_IN_LIST + " AND t.excludedFromBudget = false";

    private TransactionPredicates() {
    }

}
