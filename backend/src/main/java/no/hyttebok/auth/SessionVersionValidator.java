package no.hyttebok.auth;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpRequest;
import io.micronaut.security.token.Claims;
import io.micronaut.security.token.jwt.validator.GenericJwtClaimsValidator;
import jakarta.inject.Singleton;

/**
 * Login cookies can't be taken back, so each carries the user's {@code sessionVersion}: once it's bumped (a new
 * password, or "log out everywhere"), older cookies are refused and those devices are logged out. Cookies from before
 * this existed have none and count as version 0.
 */
@Singleton
public class SessionVersionValidator implements GenericJwtClaimsValidator<HttpRequest<?>> {

    static final String CLAIM = "sessionVersion";

    private final AppUserRepository users;

    public SessionVersionValidator(AppUserRepository users) {
        this.users = users;
    }

    // ponytail: one small query per request, like the membership check; cache it if it shows up in timings.
    @Override
    public boolean validate(Claims claims, @Nullable HttpRequest<?> request) {

        if (!(claims.get("userId") instanceof Number userId)) {
            return true; // not one of our login cookies
        }
        var version = claims.get(CLAIM) instanceof Number n ? n.intValue() : 0;

        return users.findSessionVersionById(userId.longValue()).map(current -> current == version).orElse(false);
    }
}
