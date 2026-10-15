package no.hyttebok.access;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.http.HttpRequest;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.rules.ConfigurationInterceptUrlMapRule;
import io.micronaut.security.rules.SecurityRule;
import io.micronaut.security.rules.SecurityRuleResult;
import jakarta.inject.Singleton;
import no.hyttebok.cabin.CabinContext;
import org.reactivestreams.Publisher;

/**
 * A cabin's data (everything under /api without its own @Secured rule) is only for members of that cabin: the
 * request must name a cabin (X-Hytte) and the user must belong to it (or be an admin in admin mode). Checked on
 * every request, so access granted or removed counts immediately. Runs after the @Secured rule (auth, access and
 * cabin endpoints decide for themselves) and before the URL map, which only knows "logged in".
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
        var cabin = CabinContext.fromRequest(request);

        var adminMode = access.isAdmin(authentication) && "on".equals(request.getHeaders().get(AccessService.ADMIN_MODE_HEADER));
        var allowed = cabin.isPresent() && (adminMode || access.role(authentication, cabin.get()) != null);

        return Publishers.just(allowed ? SecurityRuleResult.UNKNOWN : SecurityRuleResult.REJECTED);
    }

    @Override
    public int getOrder() {
        return ConfigurationInterceptUrlMapRule.ORDER - 50;
    }
}
