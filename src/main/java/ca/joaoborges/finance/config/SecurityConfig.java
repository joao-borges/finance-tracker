package ca.joaoborges.finance.config;

import ca.joaoborges.finance.auth.AllowlistOidcUserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

/**
 * Google sign-in gate. When a Google client is configured (prod, behind
 * Cloudflare) the whole app requires an allowlisted Google account; API calls
 * get a 401 (so the SPA can redirect) while page loads redirect to Google.
 * When no client is configured (local dev) everything is open, so running
 * without credentials still works.
 */
@Configuration
@EnableWebSecurity
@Slf4j
public class SecurityConfig {

    private static final String[] PUBLIC_PATHS = {
            "/api/ping", "/actuator/health", "/oauth2/**", "/login/**", "/error",
            // Branding/PWA assets — reachable without auth so the browser/iOS icon
            // fetcher (which carries no session) can load them.
            "/icons/**",
    };

    @Bean
    public SecurityFilterChain securityFilterChain(final HttpSecurity http,
                                                   final ObjectProvider<ClientRegistrationRepository> clients,
                                                   final AllowlistOidcUserService oidcUserService) throws Exception {
        // Outbound RestTemplate / SimpleFIN / Discord are server-side; no CSRF token
        // flow in the SPA. We rely on a SameSite=Lax session cookie instead.
        http.csrf(csrf -> csrf.disable());

        if (clients.getIfAvailable() == null) {
            log.warn("No Google OAuth client configured — running with authentication DISABLED (dev mode).");
            http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
            return http.build();
        }

        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .anyRequest().authenticated())
                .oauth2Login(oauth -> oauth
                        .userInfoEndpoint(info -> info.oidcUserService(oidcUserService))
                        .defaultSuccessUrl("/", true))
                .logout(logout -> logout.logoutSuccessUrl("/"))
                // API callers get 401 (the SPA turns that into a login redirect);
                // browser navigations fall through to the oauth2 login redirect.
                .exceptionHandling(ex -> ex.defaultAuthenticationEntryPointFor(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                        request -> request.getRequestURI().startsWith("/api/")));
        return http.build();
    }

}
