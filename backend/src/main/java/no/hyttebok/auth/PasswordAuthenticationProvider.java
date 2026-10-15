package no.hyttebok.auth;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpRequest;
import io.micronaut.security.authentication.AuthenticationFailureReason;
import io.micronaut.security.authentication.AuthenticationRequest;
import io.micronaut.security.authentication.AuthenticationResponse;
import io.micronaut.security.authentication.provider.HttpRequestAuthenticationProvider;
import jakarta.inject.Singleton;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Email + password login (POST /login). After {@value #MAX_FAILURES} wrong passwords for an email, that email
 * is locked for 15 minutes, so passwords can't be guessed in bulk.
 */
@Singleton
public class PasswordAuthenticationProvider<B> implements HttpRequestAuthenticationProvider<B> {

    private static final int MAX_FAILURES = 5;
    private static final Duration LOCK = Duration.ofMinutes(15);

    private final AppUserRepository users;
    // ponytail: in memory, per email; a restart forgets it. Fine for one server.
    private final Map<String, Failures> failures = new ConcurrentHashMap<>();

    private record Failures(int count, Instant since) {
    }

    public PasswordAuthenticationProvider(AppUserRepository users) {
        this.users = users;
    }

    @Override
    public AuthenticationResponse authenticate(@Nullable HttpRequest<B> request, AuthenticationRequest<String, String> credentials) {

        var email = credentials.getIdentity() == null ? "" : credentials.getIdentity().trim().toLowerCase(Locale.ROOT);
        var password = credentials.getSecret() == null ? "" : credentials.getSecret();
        var now = Instant.now();

        var failed = failures.get(email);
        if (failed != null && failed.since().plus(LOCK).isBefore(now)) {
            failures.remove(email);
            failed = null;
        }
        if (failed != null && failed.count() >= MAX_FAILURES) {
            return AuthenticationResponse.failure(AuthenticationFailureReason.ACCOUNT_LOCKED);
        }

        var user = users.findByEmail(email).filter(u -> u.passwordHash() != null && Passwords.matches(password, u.passwordHash()));
        if (user.isEmpty()) {
            failures.merge(email, new Failures(1, now), (old, one) -> new Failures(old.count() + 1, old.since()));
            return AuthenticationResponse.failure(AuthenticationFailureReason.CREDENTIALS_DO_NOT_MATCH);
        }

        failures.remove(email);
        return UserSessions.success(users.update(user.get().withLogin(now)));
    }
}
