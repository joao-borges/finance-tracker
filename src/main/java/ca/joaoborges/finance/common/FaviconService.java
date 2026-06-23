package ca.joaoborges.finance.common;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Resolves a logo URL from a website by pointing at a favicon service. Storing
 * the resolved URL (rather than downloading bytes) keeps phase 1 simple and
 * offline-testable; the UI just renders the URL. Used by accounts and merchants.
 */
@Service
public class FaviconService {

    private static final String FAVICON_TEMPLATE = "https://www.google.com/s2/favicons?sz=64&domain=%s";

    /**
     * Returns a favicon URL for the given website, or {@code null} when the
     * website is blank or has no resolvable host.
     */
    public String resolveLogoUrl(final String website) {
        final String host = hostOf(website);
        if (host == null) {
            return null;
        }
        return FAVICON_TEMPLATE.formatted(host);
    }

    private String hostOf(final String website) {
        if (!StringUtils.hasText(website)) {
            return null;
        }
        final String trimmed = website.trim();
        final String withScheme = trimmed.contains("://") ? trimmed : "https://" + trimmed;
        try {
            final String host = new URI(withScheme).getHost();
            if (!StringUtils.hasText(host)) {
                return null;
            }
            return host.startsWith("www.") ? host.substring(4) : host;
        } catch (final URISyntaxException ignored) {
            return null;
        }
    }

}
