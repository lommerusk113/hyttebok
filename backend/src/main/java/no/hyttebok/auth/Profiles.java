package no.hyttebok.auth;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;
import no.hyttebok.access.AccessService;
import no.hyttebok.access.MembershipRepository;
import no.hyttebok.cabin.CabinInfoRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * A person's profile: who they are, how they log in and their cabins. The same for "Min profil" and for a system
 * admin looking someone up. Also the two ways to a new password without email: a one-time link, or setting it.
 */
@Singleton
public class Profiles {

    private static final Duration RESET_VALID = Duration.ofDays(1);
    private static final int TOKEN_BYTES = 18;
    private static final int RECOVERY_CODES = 8;
    // No 0/o, 1/l/i: the codes are often written down by hand.
    private static final String CODE_ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789";
    private static final int CODE_LENGTH = 10;

    private final AppUserRepository users;
    private final MembershipRepository memberships;
    private final CabinInfoRepository cabins;
    private final PasswordResetRepository resets;
    private final RecoveryCodeRepository recoveryCodes;
    private final AccessService access;
    private final SecureRandom random = new SecureRandom();

    public Profiles(AppUserRepository users, MembershipRepository memberships, CabinInfoRepository cabins, PasswordResetRepository resets,
                    RecoveryCodeRepository recoveryCodes, AccessService access) {
        this.users = users;
        this.memberships = memberships;
        this.cabins = cabins;
        this.resets = resets;
        this.recoveryCodes = recoveryCodes;
        this.access = access;
    }

    /**
     * {@code google}/{@code password}: the ways this person can log in. {@code admin}: a system admin (ADMIN_EMAILS, via
     * Google). {@code recoveryCodes}: how many unused recovery codes they have.
     */
    @Serdeable
    public record Profile(Long id, String name, String email, @Nullable String picture, boolean google, boolean password, boolean admin,
                          Instant createdAt, Instant lastLoginAt, List<ProfileCabin> cabins, long recoveryCodes) {
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
            user.passwordHash() != null, user.emailVerified() && access.isAdmin(user.email()), user.createdAt(), user.lastLoginAt(), list,
            recoveryCodes.countByUserIdAndUsedAtIsNull(userId));
    }

    public void setPassword(Long userId, String password) {
        users.update(user(userId).withNewPassword(Passwords.hash(password)));
    }

    /** A one-time link to set a new password, valid for a day; shared by hand (there is no email service). */
    public ResetLink resetLink(Long userId, Long createdBy) {

        user(userId);
        var bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        var reset = resets.save(new PasswordReset(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes), userId, createdBy, Instant.now().plus(RESET_VALID), false));

        return new ResetLink(reset.token(), reset.expiresAt());
    }

    /** Every device is logged out, this one too. */
    public void logOutEverywhere(Long userId) {
        users.update(user(userId).loggedOutEverywhere());
    }

    /** New recovery codes (the old ones stop working), shown once: only their hashes are kept. */
    @Transactional
    public List<String> newRecoveryCodes(Long userId) {

        user(userId);
        recoveryCodes.deleteByUserId(userId);
        var codes = new ArrayList<String>();
        for (var i = 0; i < RECOVERY_CODES; i++) {
            var code = new StringBuilder();
            for (var c = 0; c < CODE_LENGTH; c++) {
                code.append(CODE_ALPHABET.charAt(random.nextInt(CODE_ALPHABET.length())));
            }
            codes.add(code.insert(CODE_LENGTH / 2, '-').toString());
            recoveryCodes.save(new RecoveryCode(null, userId, hash(codes.getLast()), null));
        }

        return codes;
    }

    /**
     * Uses up one recovery code and gives a reset link for it, or empty if the email and code don't match. Codes have
     * ~49 bits of randomness, so they can't be guessed even without a lockout.
     */
    @Transactional
    public Optional<ResetLink> redeem(String email, String code) {

        var normalized = code.trim().toLowerCase(Locale.ROOT).replace(" ", "");
        if (normalized.length() == CODE_LENGTH) {
            normalized = normalized.substring(0, CODE_LENGTH / 2) + "-" + normalized.substring(CODE_LENGTH / 2);
        }
        var hash = hash(normalized);

        return users.findByEmail(email.trim())
            .flatMap(u -> recoveryCodes.findByUserIdAndCodeHashAndUsedAtIsNull(u.id(), hash))
            .map(found -> {
                recoveryCodes.update(new RecoveryCode(found.id(), found.userId(), found.codeHash(), Instant.now()));
                return resetLink(found.userId(), found.userId());
            });
    }

    private static String hash(String code) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(code.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private AppUser user(Long userId) {
        return users.findById(userId).orElseThrow(() -> new HttpStatusException(HttpStatus.NOT_FOUND, "No such user"));
    }
}
