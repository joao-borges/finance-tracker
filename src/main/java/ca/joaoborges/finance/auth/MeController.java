package ca.joaoborges.finance.auth;

import lombok.Builder;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The signed-in user, for the UI to show who's logged in. When auth is disabled
 * (dev) the principal is null and {@code authenticated} is false.
 */
@RestController
@RequestMapping("/api/me")
public class MeController {

    @Builder
    public record Me(boolean authenticated, String email, String name, String picture) {
    }

    @GetMapping
    public Me me(@AuthenticationPrincipal final OidcUser user) {
        if (user == null) {
            return Me.builder().authenticated(false).build();
        }
        return Me.builder()
                .authenticated(true)
                .email(user.getEmail())
                .name(user.getFullName())
                .picture(user.getPicture())
                .build();
    }

}
