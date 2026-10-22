package no.hyttebok.auth;

import io.micronaut.security.authentication.AuthenticationResponse;

import java.util.List;
import java.util.Map;

/** What goes into the login cookie, the same whether someone logged in with Google or a password. */
final class UserSessions {

    private UserSessions() {
    }

    static AuthenticationResponse success(AppUser user) {

        var attributes = Map.<String, Object>of(
            "userId", user.id(),
            "email", user.email(),
            "name", user.name() == null ? user.email() : user.name(),
            "picture", user.picture() == null ? "" : user.picture(),
            "emailVerified", user.emailVerified(),
            SessionVersionValidator.CLAIM, user.sessionVersion());

        return AuthenticationResponse.success("user-" + user.id(), List.of(), attributes);
    }
}
