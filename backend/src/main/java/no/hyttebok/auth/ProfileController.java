package no.hyttebok.auth;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.rules.SecurityRule;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import no.hyttebok.access.AccessService;

/** "Min profil": your account, your cabins, and changing your password. */
@Controller("/api/profile")
@Secured(SecurityRule.IS_AUTHENTICATED)
public class ProfileController {

    private final Profiles profiles;
    private final AppUserRepository users;

    public ProfileController(Profiles profiles, AppUserRepository users) {
        this.profiles = profiles;
        this.users = users;
    }

    /** {@code current}: the password you have now; not needed if you only log in with Google (this adds a password). */
    @Serdeable
    public record PasswordChange(@Nullable String current, @NotBlank @Size(min = Passwords.MIN_LENGTH, max = Passwords.MAX_LENGTH) String password) {
    }

    @Get
    public Profiles.Profile me(Authentication auth) {
        return profiles.profile(AccessService.userId(auth), false);
    }

    @Put("/password")
    public void changePassword(Authentication auth, @Body @Valid PasswordChange body) {

        var userId = AccessService.userId(auth);
        var hash = users.findById(userId).map(AppUser::passwordHash).orElse(null);
        if (hash != null && (body.current() == null || !Passwords.matches(body.current(), hash))) {
            throw new HttpStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Wrong current password");
        }
        // ponytail: other logged-in devices stay logged in; end sessions on change if a stolen password becomes a worry.
        profiles.setPassword(userId, body.password());
    }
}
