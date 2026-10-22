package no.hyttebok.auth;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.security.authentication.AuthenticationResponse;
import io.micronaut.security.oauth2.endpoint.authorization.state.State;
import io.micronaut.security.oauth2.endpoint.token.response.OpenIdAuthenticationMapper;
import io.micronaut.security.oauth2.endpoint.token.response.OpenIdClaims;
import io.micronaut.security.oauth2.endpoint.token.response.OpenIdTokenResponse;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;

import java.time.Instant;

/** After Google's login: remember the person (create, update or link the user row) and put their details in the session. */
@Singleton
@Named("google")
public class GoogleUserMapper implements OpenIdAuthenticationMapper {

    private final AppUserRepository users;

    public GoogleUserMapper(AppUserRepository users) {
        this.users = users;
    }

    @Override
    public Publisher<AuthenticationResponse> createAuthenticationResponse(String providerName,
                                                                         OpenIdTokenResponse tokenResponse,
                                                                         OpenIdClaims claims,
                                                                         @Nullable State state) {

        var now = Instant.now();
        var user = users.findByGoogleSub(claims.getSubject())
            .map(u -> users.update(new AppUser(u.id(), u.googleSub(), claims.getEmail(), claims.getName(), claims.getPicture(), u.createdAt(), now, u.passwordHash(), u.sessionVersion())))
            // Same email as a password account: one person, one account. Google has now proven the email is theirs,
            // so the password (set by whoever signed up with it) is dropped, and so is every session it opened.
            .or(() -> users.findByEmail(claims.getEmail())
                .map(u -> users.update(new AppUser(u.id(), claims.getSubject(), claims.getEmail(), u.name() == null ? claims.getName() : u.name(),
                    claims.getPicture(), u.createdAt(), now, null, u.sessionVersion() + 1))))
            .orElseGet(() -> users.save(new AppUser(null, claims.getSubject(), claims.getEmail(), claims.getName(), claims.getPicture(), now, now, null, 0)));

        return Publishers.just(UserSessions.success(user));
    }
}
