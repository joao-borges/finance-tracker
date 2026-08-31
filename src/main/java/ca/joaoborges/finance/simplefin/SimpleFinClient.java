package ca.joaoborges.finance.simplefin;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

/**
 * Thin SimpleFIN HTTP client over a shared {@link RestTemplate} (Apache
 * HttpClient 5). The setup token is base64 of a one-time claim URL; POSTing it
 * returns a long-lived access URL with embedded credentials. The accounts
 * endpoint is that access URL + "/accounts" with HTTP Basic auth; a
 * {@code start-date} is required for the bridge to return transactions (balances
 * come back regardless), and an optional {@code end-date} bounds the window.
 */
@Component
@RequiredArgsConstructor
public class SimpleFinClient {

    private final RestTemplate restTemplate;

    /** Exchange a setup token for a long-lived access URL (one-time). */
    public String claim(final String setupToken) {
        final String claimUrl = new String(Base64.getDecoder().decode(setupToken.trim()), StandardCharsets.UTF_8).trim();
        try {
            final ResponseEntity<String> response =
                    restTemplate.exchange(URI.create(claimUrl), HttpMethod.POST, HttpEntity.EMPTY, String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new IllegalStateException("SimpleFIN claim failed (HTTP " + response.getStatusCode().value() + ")");
            }
            return response.getBody().trim();
        } catch (final RestClientException networkError) {
            throw new IllegalStateException("SimpleFIN claim request failed", networkError);
        }
    }

    /**
     * Fetch just the accounts + balances (no transactions) — the lightweight
     * live health probe behind the on-demand health check.
     */
    public String fetchBalances(final String accessUrl) {
        final URI uri = URI.create(accessUrl.trim());
        final UriComponentsBuilder builder = UriComponentsBuilder.fromUri(withoutUserInfo(uri))
                .path("/accounts")
                .queryParam("balances-only", 1);
        return get(builder.build(true).toUri(), uri.getUserInfo());
    }

    /** Fetch the accounts/transactions payload as raw JSON for [startDate, endDate). */
    public String fetchAccounts(final String accessUrl, final Instant startDate, final Instant endDate) {
        final URI uri = URI.create(accessUrl.trim());
        final String userInfo = uri.getUserInfo();
        final UriComponentsBuilder builder = UriComponentsBuilder.fromUri(withoutUserInfo(uri))
                .path("/accounts")
                .queryParam("start-date", startDate.getEpochSecond());
        if (endDate != null) {
            builder.queryParam("end-date", endDate.getEpochSecond());
        }
        return get(builder.build(true).toUri(), userInfo);
    }

    private String get(final URI endpoint, final String userInfo) {

        final HttpHeaders headers = new HttpHeaders();
        if (userInfo != null) {
            headers.set(HttpHeaders.AUTHORIZATION,
                    "Basic " + Base64.getEncoder().encodeToString(userInfo.getBytes(StandardCharsets.UTF_8)));
        }
        try {
            final ResponseEntity<String> response =
                    restTemplate.exchange(endpoint, HttpMethod.GET, new HttpEntity<>(headers), String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new IllegalStateException("SimpleFIN fetch failed (HTTP " + response.getStatusCode().value() + ")");
            }
            return response.getBody();
        } catch (final RestClientException networkError) {
            throw new IllegalStateException("SimpleFIN fetch request failed", networkError);
        }
    }

    private URI withoutUserInfo(final URI uri) {
        try {
            return new URI(uri.getScheme(), null, uri.getHost(), uri.getPort(), uri.getPath(), uri.getQuery(), null);
        } catch (final URISyntaxException invalid) {
            throw new IllegalStateException("Invalid SimpleFIN access URL", invalid);
        }
    }

}
