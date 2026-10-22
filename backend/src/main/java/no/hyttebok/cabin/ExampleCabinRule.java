package no.hyttebok.cabin;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.rules.SecurityRule;
import io.micronaut.security.rules.SecurityRuleResult;
import jakarta.inject.Singleton;
import no.hyttebok.access.MemberRule;
import org.reactivestreams.Publisher;

/**
 * The example cabin can be read by anyone, logged in or not, so visitors can look around before signing up. Only
 * reads (GET) of a cabin's own data: endpoints with their own @Secured rule (access, admin, profile, auth) decide
 * before this, push subscriptions are left out, and every change still needs a member.
 */
@Singleton
public class ExampleCabinRule implements SecurityRule<HttpRequest<?>> {

    private final CabinInfoRepository cabins;
    private final MemberRule members;

    public ExampleCabinRule(CabinInfoRepository cabins, MemberRule members) {
        this.cabins = cabins;
        this.members = members;
    }

    // ponytail: one small query per read, like the membership check; cache the id if it shows up in timings.
    @Override
    public Publisher<SecurityRuleResult> check(@Nullable HttpRequest<?> request, @Nullable Authentication authentication) {

        if (request == null || request.getMethod() != HttpMethod.GET || !request.getPath().startsWith("/api/") || request.getPath().startsWith("/api/push")) {
            return Publishers.just(SecurityRuleResult.UNKNOWN);
        }
        var cabin = CabinContext.fromRequest(request);
        var example = cabin.isPresent() && cabin.equals(cabins.findExampleId());

        return Publishers.just(example ? SecurityRuleResult.ALLOWED : SecurityRuleResult.UNKNOWN);
    }

    /** Before the membership check, which would turn visitors away. */
    @Override
    public int getOrder() {
        return members.getOrder() - 10;
    }
}
