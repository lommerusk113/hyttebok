package no.hyttebok.auth;

import io.micronaut.context.annotation.Value;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.http.HttpRequest;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.rules.SecuredAnnotationRule;
import io.micronaut.security.rules.SecurityRule;
import io.micronaut.security.rules.SecurityRuleResult;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;

/**
 * Login is on once Google is set up (GOOGLE_CLIENT_ID) or system admins are named (ADMIN_EMAILS); email +
 * password then works too. Before that, everything is allowed, as before login existed. When on, this rule
 * stays out of the way and /api/** requires a logged-in member (application.properties, MemberRule).
 */
@Singleton
public class LoginOffRule implements SecurityRule<HttpRequest<?>> {

    private final boolean googleOn;
    private final boolean loginOn;

    public LoginOffRule(@Value("${micronaut.security.oauth2.clients.google.client-id:}") String clientId,
                        @Value("${hyttebok.admin-emails:}") String adminEmails) {
        this.googleOn = !clientId.isBlank();
        this.loginOn = googleOn || !adminEmails.isBlank();
    }

    public boolean loginOn() {
        return loginOn;
    }

    public boolean googleOn() {
        return googleOn;
    }

    @Override
    public Publisher<SecurityRuleResult> check(@Nullable HttpRequest<?> request, @Nullable Authentication authentication) {
        return Publishers.just(loginOn ? SecurityRuleResult.UNKNOWN : SecurityRuleResult.ALLOWED);
    }

    @Override
    public int getOrder() {
        return SecuredAnnotationRule.ORDER - 100;
    }
}
