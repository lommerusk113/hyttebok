package no.hyttebok.access;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.http.HttpRequest;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.rules.ConfigurationInterceptUrlMapRule;
import io.micronaut.security.rules.SecurityRule;
import io.micronaut.security.rules.SecurityRuleResult;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;

/**
 * The cabin's data (everything under /api without its own @Secured rule) is for members only. Logged in but
 * not a member = 403, checked on every request so access granted or removed counts immediately.
 * Runs after the @Secured rule (auth and access endpoints decide for themselves) and before the URL map,
 * which only knows "logged in".
 */
@Singleton
public class MemberRule implements SecurityRule<HttpRequest<?>> {

    private final AccessService access;

    public MemberRule(AccessService access) {
        this.access = access;
    }

    @Override
    public Publisher<SecurityRuleResult> check(@Nullable HttpRequest<?> request, @Nullable Authentication authentication) {

        if (request == null || authentication == null || !request.getPath().startsWith("/api/")) {
            return Publishers.just(SecurityRuleResult.UNKNOWN);
        }

        return Publishers.just(access.role(authentication) != null ? SecurityRuleResult.UNKNOWN : SecurityRuleResult.REJECTED);
    }

    @Override
    public int getOrder() {
        return ConfigurationInterceptUrlMapRule.ORDER - 50;
    }
}
