package no.hyttebok.auth;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.rules.SecurityRule;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import no.hyttebok.access.AccessService;
import no.hyttebok.access.Membership;
import no.hyttebok.access.MembershipRepository;
import no.hyttebok.audit.Audit;

import java.util.List;

/** "Min profil": your account, your cabins (and leaving one), your password, recovery codes and logging out everywhere. */
@Controller("/api/profile")
@Secured(SecurityRule.IS_AUTHENTICATED)
public class ProfileController {

    private final Profiles profiles;
    private final AppUserRepository users;
    private final MembershipRepository memberships;
    private final Audit audit;

    public ProfileController(Profiles profiles, AppUserRepository users, MembershipRepository memberships, Audit audit) {
        this.profiles = profiles;
        this.users = users;
        this.memberships = memberships;
        this.audit = audit;
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
        // Logs out every device, this one too: the app logs in again with the new password.
        profiles.setPassword(userId, body.password());
    }

    @Post("/logout-everywhere")
    public void logOutEverywhere(Authentication auth) {

        var userId = AccessService.userId(auth);
        profiles.logOutEverywhere(userId);
        audit.log(null, auth, Audit.SIGNED_OUT_EVERYWHERE, userId, null);
    }

    /** New recovery codes, shown this once; the old ones stop working. */
    @Post("/recovery-codes")
    public List<String> recoveryCodes(Authentication auth) {
        return profiles.newRecoveryCodes(AccessService.userId(auth));
    }

    /** Leave a cabin. The last owner can't: someone else must be made owner first (or the cabin deleted). */
    @Delete("/cabins/{cabinId}")
    @Transactional
    public void leave(Authentication auth, Long cabinId) {

        var userId = AccessService.userId(auth);
        var member = memberships.findByUserIdAndCabinId(userId, cabinId);
        if (member.isEmpty()) {
            return;
        }

        var owners = memberships.findByCabinId(cabinId).stream().filter(m -> Membership.OWNER.equals(m.role())).count();
        if (Membership.OWNER.equals(member.get().role()) && owners <= 1) {
            throw new HttpStatusException(HttpStatus.CONFLICT, "The last owner can't leave");
        }
        memberships.deleteByUserIdAndCabinId(userId, cabinId);
        audit.log(cabinId, auth, Audit.MEMBER_LEFT, null, null);
    }
}
