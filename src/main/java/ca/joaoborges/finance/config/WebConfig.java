package ca.joaoborges.finance.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;
import java.time.Duration;

/**
 * SPA fallback: the React app uses client-side routing, so a direct hit on e.g.
 * {@code /transactions} has no server mapping. Serve the real static asset when
 * one exists, otherwise fall back to {@code index.html} and let the SPA route.
 * API and actuator paths are left to their controllers (and never fall back).
 *
 * <p>Cache policy: Vite bundles under {@code /assets/} are content-hashed, so
 * they cache forever; {@code index.html} (and every SPA-fallback response) is
 * {@code no-cache} so browsers revalidate and pick up new bundle hashes right
 * after a deploy — without it, a heuristically-cached index.html keeps serving
 * the previous release.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(final ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/assets/**")
                .addResourceLocations("classpath:/static/assets/")
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).immutable());
        registry.addResourceHandler("/icons/**")
                .addResourceLocations("classpath:/static/icons/")
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(7)));
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .setCacheControl(CacheControl.noCache())
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(final String resourcePath, final Resource location) throws IOException {
                        final Resource requested = location.createRelative(resourcePath);
                        if (requested.exists() && requested.isReadable()) {
                            return requested;
                        }
                        if (resourcePath.startsWith("api/") || resourcePath.startsWith("actuator/")) {
                            return null;
                        }
                        return new ClassPathResource("/static/index.html");
                    }
                });
    }

}
