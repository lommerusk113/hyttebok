package no.hyttebok.auth;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.inject.Singleton;
import no.hyttebok.access.AccessService;
import no.hyttebok.access.MembershipRepository;
import no.hyttebok.cabin.CabinInfoRepository;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Set;

/**
 * A person's profile: who they are, how they log in and their cabins. The same for "Min profil" and for a system
 * admin looking someone up. Also the two ways to a new password without email: a one-time link, or setting it.
 */
@Singleton
public class Profiles {

    private static final Duration RESET_VALID = Duration.ofDays(1);
    private static final int TOKEN_BYTES = 18;

    private final AppUserRepository users;
    private final MembershipRepository memberships;
    private final CabinInfoRepository cabins;
    private final PasswordResetRepository resets;
    private final AccessService access;
    private final SecureRandom random = new SecureRandom();

    public Profiles(AppUserRepository users, MembershipRepository memberships, CabinInfoRepository cabins, PasswordResetRepository resets, AccessService access) {
        this.users = users;
        this.memberships = memberships;
        this.cabins = cabins;
        this.resets = resets;
        this.access = access;
    }

    /** {@code google}/{@code password}: the ways this person can log in. {@code admin}: a system admin (ADMIN_EMAILS, via Google). */
    @Serdeable
    public record Profile(Long id, String name, String email, @Nullable String picture, boolean google, boolean password, boolean admin,
                          Instant createdAt, Instant lastLoginAt, List<ProfileCabin> cabins) {
    }

    @Serdeable
    public record ProfileCabin(Long id, String name, String role, boolean deleted) {
    }

    @Serdeable
    public record ResetLink(String token, Instant expiresAt) {
    }

    /** {@code withDeleted}: also the deleted cabins (for admins; the person themselves no longer sees them). */
    public Profile profile(Long userId, boolean withDeleted) {

        var user = user(userId);
        var roles = memberships.findByUserId(userId);
        var deleted = Set.copyOf(cabins.findDeletedIds());
        var names = cabins.findByIdInOrderByName(roles.stream().map(m -> m.cabinId()).toList());
        var list = names.stream().filter(c -> withDeleted || !deleted.contains(c.id()))
            .map(c -> new ProfileCabin(c.id(), c.name(), roles.stream().filter(m -> m.cabinId().equals(c.id())).findFirst().orElseThrow().role(), deleted.contains(c.id())))
            .toList();

        return new Profile(user.id(), user.name() == null ? user.email() : user.name(), user.email(), user.picture(), user.googleSub() != null,
            user.passwordHash() != null, user.emailVerified() && access.isAdmin(user.email()), user.createdAt(), user.lastLoginAt(), list);
    }

    public void setPassword(Long userId, String password) {
        users.update(user(userId).withPasswordHash(Passwords.hash(password)));
    }

    /** A one-time link to set a new password, valid for a day; shared by hand (there is no email service). */
    public ResetLink resetLink(Long userId, Long createdBy) {

        user(userId);
        var bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        var reset = resets.save(new PasswordReset(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes), userId, createdBy, Instant.now().plus(RESET_VALID), false));

        return new ResetLink(reset.token(), reset.expiresAt());
    }

    private AppUser user(Long userId) {
        return users.findById(userId).orElseThrow(() -> new HttpStatusException(HttpStatus.NOT_FOUND, "No such user"));
    }
}
