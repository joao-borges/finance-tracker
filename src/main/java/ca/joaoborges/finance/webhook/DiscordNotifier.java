package ca.joaoborges.finance.webhook;

import ca.joaoborges.finance.ingest.ImportSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Posts notifications to a Discord channel webhook (the only notification channel
 * we support). Fire-and-forget — the POST runs on a background thread over the
 * shared {@link RestTemplate} so a slow/broken webhook never blocks an import.
 * No-op when no webhook URL is configured.
 */
@Service
public class DiscordNotifier {

    private static final Logger log = LoggerFactory.getLogger(DiscordNotifier.class);
    private static final NumberFormat MONEY = NumberFormat.getCurrencyInstance(Locale.CANADA);
    private static final int RED = 0xE53935;
    private static final int YELLOW = 0xF1C40F;

    private final RestTemplate restTemplate;
    private final String webhookUrl;

    public DiscordNotifier(final RestTemplate restTemplate,
                           @Value("${finance.discord.webhook-url:}") final String webhookUrl) {
        this.restTemplate = restTemplate;
        this.webhookUrl = webhookUrl;
    }

    public void sendImportSummary(final ImportSummary summary) {
        if (!StringUtils.hasText(webhookUrl)) {
            return;
        }
        send(buildContent(summary));
    }

    /** Red alert: a category's monthly spend went over its budget. */
    public void sendBudgetOverflow(final String category, final String month,
                                   final BigDecimal spent, final BigDecimal planned) {
        sendEmbed("🔴 Over budget: " + category, RED,
                "Spent " + money(spent) + " of " + money(planned) + " budgeted for " + month
                        + " (over by " + money(spent.subtract(planned)) + ").");
    }

    /** Yellow alert: the SimpleFIN bridge reported connection problems during a sync. */
    public void sendSyncIssues(final List<String> issues) {
        sendEmbed("🔶 SimpleFIN needs attention", YELLOW,
                String.join("\n", issues)
                        + "\nRe-connect at the SimpleFIN bridge, then run a range import to catch up.");
    }

    /** Yellow alert: a category's monthly spend crossed its configured threshold. */
    public void sendThresholdAlert(final String category, final String month,
                                   final BigDecimal spent, final BigDecimal threshold) {
        sendEmbed("🟡 Threshold reached: " + category, YELLOW,
                "Spent " + money(spent) + " for " + month + " (threshold " + money(threshold) + ").");
    }

    private void sendEmbed(final String title, final int color, final String description) {
        if (!StringUtils.hasText(webhookUrl)) {
            return;
        }
        final String body = "{\"embeds\":[{\"title\":\"" + escape(title) + "\",\"description\":\""
                + escape(description) + "\",\"color\":" + color + "}]}";
        post(body);
    }

    private String money(final BigDecimal value) {
        return MONEY.format(value == null ? BigDecimal.ZERO : value);
    }

    private String buildContent(final ImportSummary summary) {
        final StringBuilder content = new StringBuilder();
        content.append("**Import complete**");
        if (StringUtils.hasText(summary.fileName())) {
            content.append(" — ").append(summary.fileName());
        }
        content.append('\n').append(summary.total()).append(" transaction(s) imported");
        for (final Map.Entry<String, Integer> entry : summary.byAccount().entrySet()) {
            content.append("\n• ").append(entry.getKey()).append(": ").append(entry.getValue());
        }
        content.append("\n✅ ").append(summary.reviewed()).append(" auto-categorized")
                .append(" · 🔎 ").append(summary.needsReview()).append(" need review");
        return content.toString();
    }

    private void send(final String content) {
        post("{\"content\":\"" + escape(content) + "\"}");
    }

    private void post(final String body) {
        final HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        final HttpEntity<String> request = new HttpEntity<>(body, headers);
        CompletableFuture.runAsync(() -> {
            try {
                restTemplate.postForEntity(webhookUrl, request, Void.class);
            } catch (final RuntimeException error) {
                log.warn("Discord webhook call failed: {}", error.getMessage());
            }
        });
    }

    private String escape(final String value) {
        final StringBuilder escaped = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            final char c = value.charAt(i);
            switch (c) {
                case '\\' -> escaped.append("\\\\");
                case '"' -> escaped.append("\\\"");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> escaped.append(c);
            }
        }
        return escaped.toString();
    }

}
