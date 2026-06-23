package ca.joaoborges.finance.ingest;

import java.util.Map;

/**
 * What an import produced, for the post-import notification: total rows, the
 * review-status split, and a per-account breakdown (canonical account names).
 */
public record ImportSummary(String fileName, int total, int reviewed, int needsReview, Map<String, Integer> byAccount) {
}
