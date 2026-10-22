package ca.joaoborges.finance.amazon;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExtensionPackagerTest {

    @Test
    void turnsAnOriginIntoAPortlessMatchPattern() {
        assertEquals("http://localhost/*", ExtensionPackager.matchPattern("http://localhost:3000"));
        assertEquals("https://finance.example.com/*", ExtensionPackager.matchPattern("https://Finance.Example.com"));
    }

    @Test
    void rejectsAnythingButAnHttpOrigin() {
        assertThrows(ResponseStatusException.class, () -> ExtensionPackager.matchPattern("javascript:alert(1)"));
        assertThrows(ResponseStatusException.class, () -> ExtensionPackager.matchPattern("not a url"));
        assertThrows(ResponseStatusException.class, () -> ExtensionPackager.matchPattern(null));
    }

    @Test
    void zipsEveryExtensionFileWithTheManifestWiredToTheApp() throws IOException {
        final Map<String, String> files = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(
                new ExtensionPackager().zip("https://finance.example.com")))) {
            for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                files.put(entry.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        assertTrue(files.keySet().containsAll(List.of(
                "finance-amazon-extension/manifest.json",
                "finance-amazon-extension/background.js",
                "finance-amazon-extension/bridge.js",
                "finance-amazon-extension/scraper.js")));
        final String manifest = files.get("finance-amazon-extension/manifest.json");
        assertTrue(manifest.contains("\"https://finance.example.com/*\""));
        assertFalse(manifest.contains("__APP_MATCH__"));
    }

}
