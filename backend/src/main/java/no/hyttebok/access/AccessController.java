package no.hyttebok.access;

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
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import no.hyttebok.auth.AppUser;
import no.hyttebok.auth.AppUserRepository;
import no.hyttebok.auth.PasswordReset;
import no.hyttebok.auth.PasswordResetRepository;
import no.hyttebok.cabin.CabinInfoRepository;
import no.hyttebok.push.Notifier;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Who has access: access requests, members and roles, invitation links, and (for system admins) every user
 * who has logged in. Logged-in non-members may only ask for access or accept an invitation.
 */
@Controller("/api/access")
@Secured(SecurityRule.IS_AUTHENTICATED)
public class AccessController {

    private static final long CABIN_ID = 1L;
    private static final Duration INVITE_VALID = Duration.ofDays(14);
    private static final Duration RESET_VALID = Duration.ofDays(1);
    private static final int TOKEN_BYTES = 18;

    private final AccessService access;
    private final AppUserRepository users;
    private final MembershipRepository memberships;
    private final InvitationRepository invitations;
    private final AccessRequestRepository requests;
    private final CabinInfoRepository cabins;
    private final PasswordResetRepository resets;
    private final Notifier notifier;
    private final SecureRandom random = new SecureRandom();

    public AccessController(AccessService access, AppUserRepository users, MembershipRepository memberships,
                            InvitationRepository invitations, AccessRequestRepository requests, CabinInfoRepository cabins,
                            PasswordResetRepository resets, Notifier notifier) {
        this.access = access;
        this.users = users;
        this.memberships = memberships;
        this.invitations = invitations;
        this.requests = requests;
        this.cabins = cabins;
        this.resets = resets;
        this.notifier = notifier;
    }

    @Serdeable
    public record Person(Long id, String name, String email, @Nullable String picture, @Nullable String role, boolean admin) {
    }

    @Serdeable
    public record PendingRequest(Long id, Person user, @Nullable String message, Instant createdAt) {
    }

    @Serdeable
    public record Invite(Long id, String token, @Nullable String createdBy, Instant expiresAt, int uses) {
    }

    @Serdeable
    public record InviteInfo(boolean valid, String cabinName, @Nullable String invitedBy) {
    }

    @Serdeable
    public record RequestBody(@Nullable @Size(max = 500) String message) {
    }

    @Serdeable
    public record RoleBody(@Pattern(regexp = "OWNER|MEMBER") String role) {
    }

    @Serdeable
    public record GrantBody(Long userId, @Pattern(regexp = "OWNER|MEMBER") String role) {
    }

    // ---- asking for access ----

    @Post("/requests")
    @io.micronaut.http.annotation.Status(HttpStatus.CREATED)
    public void request(Authentication auth, @Body @Valid RequestBody body) {

        var userId = AccessService.userId(auth);
        if (memberships.existsById(userId) || requests.findFirstByUserIdAndStatus(userId, AccessRequest.PENDING).isPresent()) {
            return; // already in, or already waiting
        }
        var message = body.message() == null || body.message().isBlank() ? null : body.message().trim();
        var saved = requests.save(new AccessRequest(null, userId, message, AccessRequest.PENDING, Instant.now(), null, null));
        notifier.accessRequested(saved.id(), userId, String.valueOf(auth.getAttributes().get("name")), message);
    }

    @Get("/requests")
    public List<PendingRequest> pending(Authentication auth) {

        access.requireManager(auth);
        var pending = requests.findByStatusOrderByCreatedAt(AccessRequest.PENDING);
        var people = people(pending.stream().map(AccessRequest::userId).toList());

        return pending.stream().filter(r -> people.containsKey(r.userId()))
            .map(r -> new PendingRequest(r.id(), people.get(r.userId()), r.message(), r.createdAt())).toList();
    }

    @Post("/requests/{id}/approve")
    public void approve(Authentication auth, Long id) {
        decide(auth, id, "APPROVED");
    }

    @Post("/requests/{id}/deny")
    public void deny(Authentication auth, Long id) {
        decide(auth, id, "DENIED");
    }

    private void decide(Authentication auth, Long id, String status) {

        access.requireManager(auth);
        var request = requests.findById(id).orElseThrow(() -> new HttpStatusException(HttpStatus.NOT_FOUND, "No such request"));
        requests.update(new AccessRequest(request.id(), request.userId(), request.message(), status, request.createdAt(), AccessService.userId(auth), Instant.now()));
        if ("APPROVED".equals(status) && !memberships.existsById(request.userId())) {
            memberships.save(new Membership(request.userId(), Membership.MEMBER, Instant.now()));
        }
    }

    // ---- members ----

    @Get("/members")
    public List<Person> members(Authentication auth) {

        access.requireManager(auth);
        var ids = StreamSupport.stream(memberships.findAll().spliterator(), false).map(Membership::userId).toList();

        return people(ids).values().stream().sorted((a, b) -> a.name().compareToIgnoreCase(b.name())).toList();
    }

    @Put("/members/{userId}")
    public void changeRole(Authentication auth, Long userId, @Body @Valid RoleBody body) {

        access.requireManager(auth);
        var member = memberships.findById(userId).orElseThrow(() -> new HttpStatusException(HttpStatus.NOT_FOUND, "Not a member"));
        memberships.update(new Membership(member.userId(), body.role(), member.createdAt()));
    }

    @Delete("/members/{userId}")
    public void removeMember(Authentication auth, Long userId) {
        access.requireManager(auth);
        memberships.deleteById(userId);
    }

    /** System admins only: everyone who has logged in, so they can give access to anyone (themselves included). */
    @Get("/users")
    public List<Person> users(Authentication auth) {

        access.requireAdmin(auth);
        var ids = StreamSupport.stream(users.findAll().spliterator(), false).map(AppUser::id).toList();

        return people(ids).values().stream().sorted((a, b) -> a.name().compareToIgnoreCase(b.name())).toList();
    }

    @Post("/members")
    public void grant(Authentication auth, @Body @Valid GrantBody body) {

        access.requireAdmin(auth);
        if (users.findById(body.userId()).isEmpty()) {
            throw new HttpStatusException(HttpStatus.NOT_FOUND, "No such user");
        }
        var role = body.role();
        memberships.findById(body.userId()).ifPresentOrElse(
            m -> memberships.update(new Membership(m.userId(), role, m.createdAt())),
            () -> memberships.save(new Membership(body.userId(), role, Instant.now())));
    }

    // ---- new password (no email service: the link is shared by hand) ----

    @Serdeable
    public record ResetLink(String token, Instant expiresAt) {
    }

    /** Owners for members, admins for anyone: a one-time link to set a new password, valid for a day. */
    @Post("/users/{userId}/password-reset")
    public ResetLink passwordReset(Authentication auth, Long userId) {

        if (!(access.isAdmin(auth) || (access.canManage(auth) && memberships.existsById(userId)))) {
            throw new HttpStatusException(HttpStatus.FORBIDDEN, "Not allowed");
        }
        if (users.findById(userId).isEmpty()) {
            throw new HttpStatusException(HttpStatus.NOT_FOUND, "No such user");
        }
        var bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        var reset = resets.save(new PasswordReset(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes), userId, AccessService.userId(auth),
            Instant.now().plus(RESET_VALID), false));

        return new ResetLink(reset.token(), reset.expiresAt());
    }

    // ---- invitations ----

    @Get("/invites")
    public List<Invite> invites(Authentication auth) {

        access.requireManager(auth);
        var now = Instant.now();
        var active = invitations.findByRevokedFalseOrderByCreatedAtDesc().stream().filter(i -> i.usable(now)).toList();
        var people = people(active.stream().map(Invitation::createdBy).filter(java.util.Objects::nonNull).toList());

        return active.stream().map(i -> new Invite(i.id(), i.token(), i.createdBy() == null || !people.containsKey(i.createdBy()) ? null : people.get(i.createdBy()).name(),
            i.expiresAt(), i.uses())).toList();
    }

    @Post("/invites")
    public Invite createInvite(Authentication auth) {

        access.requireManager(auth);
        var bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        var now = Instant.now();
        var saved = invitations.save(new Invitation(null, Base64.getUrlEncoder().withoutPadding().encodeToString(bytes), AccessService.userId(auth),
            now, now.plus(INVITE_VALID), false, 0));

        return new Invite(saved.id(), saved.token(), String.valueOf(auth.getAttributes().get("name")), saved.expiresAt(), 0);
    }

    @Delete("/invites/{id}")
    public void revokeInvite(Authentication auth, Long id) {

        access.requireManager(auth);
        invitations.findById(id).ifPresent(i -> invitations.update(new Invitation(i.id(), i.token(), i.createdBy(), i.createdAt(), i.expiresAt(), true, i.uses())));
    }

    /** Shown before login on the invitation page: which cabin, and who invited you. */
    @Get("/invites/{token}/info")
    @Secured(SecurityRule.IS_ANONYMOUS)
    public InviteInfo inviteInfo(String token) {

        var cabinName = cabins.findById(CABIN_ID).map(c -> c.name()).orElse("Hyttebok");
        var invite = invitations.findByToken(token).filter(i -> i.usable(Instant.now()));
        var invitedBy = invite.map(Invitation::createdBy).flatMap(users::findById).map(u -> u.name() == null ? u.email() : u.name()).orElse(null);

        return new InviteInfo(invite.isPresent(), cabinName, invitedBy);
    }

    @Post("/invites/{token}/accept")
    public void accept(Authentication auth, String token) {

        var invite = invitations.findByToken(token).filter(i -> i.usable(Instant.now()))
            .orElseThrow(() -> new HttpStatusException(HttpStatus.GONE, "This invitation has expired or was revoked"));
        var userId = AccessService.userId(auth);
        if (!memberships.existsById(userId)) {
            memberships.save(new Membership(userId, Membership.MEMBER, Instant.now()));
            invitations.update(new Invitation(invite.id(), invite.token(), invite.createdBy(), invite.createdAt(), invite.expiresAt(), false, invite.uses() + 1));
        }
        requests.findFirstByUserIdAndStatus(userId, AccessRequest.PENDING)
            .ifPresent(r -> requests.update(new AccessRequest(r.id(), r.userId(), r.message(), "APPROVED", r.createdAt(), invite.createdBy(), Instant.now())));
    }

    private Map<Long, Person> people(List<Long> ids) {

        if (ids.isEmpty()) {
            return Map.of();
        }
        var roles = StreamSupport.stream(memberships.findAll().spliterator(), false).collect(Collectors.toMap(Membership::userId, Membership::role));

        return users.findByIdIn(ids).stream()
            .map(u -> new Person(u.id(), u.name() == null ? u.email() : u.name(), u.email(), u.picture(), roles.get(u.id()), u.emailVerified() && access.isAdmin(u.email())))
            .collect(Collectors.toMap(Person::id, Function.identity()));
    }
}
