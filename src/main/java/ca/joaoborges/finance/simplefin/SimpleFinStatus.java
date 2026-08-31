package ca.joaoborges.finance.simplefin;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;

/**
 * Connection status for the UI — never exposes the access URL. {@code bridgeUrl}
 * is only that URL's origin (scheme + host + port), with the embedded
 * credentials and path stripped, so the UI can open the bridge's own site.
 */
public record SimpleFinStatus(boolean connected, Instant lastSyncedAt, String bridgeUrl) {

    /** Build the UI-facing status from a connection, or the unconnected status for {@code null}. */
    public static SimpleFinStatus of(final SimpleFinConnection connection) {
        if (connection == null) {
            return new SimpleFinStatus(false, null, null);
        }
        return new SimpleFinStatus(true, connection.getLastSyncedAt(), bridgeOrigin(connection.getAccessUrl()));
    }

    /** Origin of the access URL, or null when it is missing or unparseable. */
    private static String bridgeOrigin(final String accessUrl) {
        if (accessUrl == null || accessUrl.isBlank()) {
            return null;
        }
        try {
            final URI uri = new URI(accessUrl.trim());
            if (uri.getScheme() == null || uri.getHost() == null) {
                return null;
            }
            return new URI(uri.getScheme(), null, uri.getHost(), uri.getPort(), null, null, null).toString();
        } catch (final URISyntaxException unparseable) {
            return null;
        }
    }

}
