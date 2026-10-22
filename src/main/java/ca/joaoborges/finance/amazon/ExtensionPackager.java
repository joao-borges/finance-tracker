package ca.joaoborges.finance.amazon;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Builds the downloadable Amazon browser extension from the sources under
 * {@code classpath:amazon-extension/}. The manifest's content-script match is
 * filled with the origin the app is served from, so the extension only talks
 * to this instance of the app.
 */
@Component
public class ExtensionPackager {

    private static final String ROOT = "amazon-extension/";
    private static final String MANIFEST = "manifest.json";
    private static final String APP_MATCH_PLACEHOLDER = "__APP_MATCH__";
    private static final Set<String> SCHEMES = Set.of("http", "https");
    private static final Pattern VERSION = Pattern.compile("\"version\"\\s*:\\s*\"([^\"]+)\"");

    private final PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

    /** The extension version the app currently ships, read from the bundled manifest. */
    public String version() {
        final Matcher matcher = VERSION.matcher(read(resolver.getResource("classpath:" + ROOT + MANIFEST)));
        return matcher.find() ? matcher.group(1) : "unknown";
    }

    /** Zip the extension for an app served at {@code appOrigin} (e.g. {@code https://finance.example.com}). */
    public byte[] zip(final String appOrigin) {
        final String match = matchPattern(appOrigin);
        try {
            final List<Resource> files = Stream.of(resolver.getResources("classpath*:" + ROOT + "**/*"))
                    .filter(Resource::isReadable)
                    .filter(resource -> resource.getFilename() != null && !resource.getFilename().isEmpty())
                    .sorted(Comparator.comparing(ExtensionPackager::relativePath))
                    .toList();
            final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(buffer)) {
                for (final Resource file : files) {
                    final String path = relativePath(file);
                    zip.putNextEntry(new ZipEntry("finance-amazon-extension/" + path));
                    if (path.equals(MANIFEST)) {
                        zip.write(read(file).replace(APP_MATCH_PLACEHOLDER, match).getBytes(StandardCharsets.UTF_8));
                    } else {
                        try (InputStream input = file.getInputStream()) {
                            input.transferTo(zip);
                        }
                    }
                    zip.closeEntry();
                }
            }
            return buffer.toByteArray();
        } catch (final IOException wrapped) {
            throw new UncheckedIOException(wrapped);
        }
    }

    /**
     * Chrome match patterns ignore the port when none is given, so
     * {@code http://localhost:3000} becomes {@code http://localhost/*}.
     */
    static String matchPattern(final String appOrigin) {
        try {
            final URI uri = new URI(appOrigin == null ? "" : appOrigin.trim());
            if (uri.getScheme() == null || !SCHEMES.contains(uri.getScheme().toLowerCase())
                    || uri.getHost() == null || uri.getHost().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "origin must be an http(s) origin");
            }
            return uri.getScheme().toLowerCase() + "://" + uri.getHost().toLowerCase() + "/*";
        } catch (final URISyntaxException rethrown) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "origin must be an http(s) origin", rethrown);
        }
    }

    private static String relativePath(final Resource resource) {
        try {
            final String url = resource.getURL().toString();
            return url.substring(url.lastIndexOf(ROOT) + ROOT.length());
        } catch (final IOException wrapped) {
            throw new UncheckedIOException(wrapped);
        }
    }

    private static String read(final Resource resource) {
        try (InputStream input = resource.getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (final IOException wrapped) {
            throw new UncheckedIOException(wrapped);
        }
    }

}
