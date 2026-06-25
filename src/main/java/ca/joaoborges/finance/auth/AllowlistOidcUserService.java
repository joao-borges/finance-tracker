package ca.joaoborges.finance.auth;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Loads the Google OIDC user, then enforces an email allowlist
 * ({@code finance.auth.allowed-emails}). Fail-closed: if the allowlist is empty
 * or the signed-in email isn't on it, the login is rejected — so only the
 * configured household accounts can get in once the app is public.
 */
@Service
@Slf4j
public class AllowlistOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {

    private final OidcUserService delegate = new OidcUserService();
    private final Set<String> allowedEmails;

    public AllowlistOidcUserService(@Value("${finance.auth.allowed-emails:}") final String allowed) {
        this.allowedEmails = StringUtils.hasText(allowed)
                ? Arrays.stream(allowed.split(","))
                        .map(email -> email.trim().toLowerCase(Locale.ROOT))
                        .filter(StringUtils::hasText)
                        .collect(Collectors.toUnmodifiableSet())
                : Set.of();
    }

    @Override
    public OidcUser loadUser(final OidcUserRequest userRequest) {
        final OidcUser user = delegate.loadUser(userRequest);
        final String email = user.getEmail();
        if (allowedEmails.isEmpty() || email == null || !allowedEmails.contains(email.toLowerCase(Locale.ROOT))) {
            log.warn("Rejected sign-in for {} (not on finance.auth.allowed-emails)", email);
            throw new OAuth2AuthenticationException(new OAuth2Error("access_denied"), "Email not allowed");
        }
        return user;
    }

}
