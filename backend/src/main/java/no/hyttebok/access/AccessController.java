package no.hyttebok.access;

import io.micronaut.context.annotation.Value;
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
import no.hyttebok.auth.AppUserRepository;
import no.hyttebok.auth.PasswordReset;
import no.hyttebok.auth.PasswordResetRepository;
import no.hyttebok.cabin.CabinContext;
import no.hyttebok.cabin.CabinInfoRepository;
import no.hyttebok.cabin.JoinCodes;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Who has access to the cabin of the request: access requests, members and roles, invitation links and the
 * join code. Owners (and admins in admin mode) manage it. There is deliberately no list of all users or cabins:
 * people get in through an invitation link (straight in) or the join code (asks the owners).
 */
@Controller("/api/access")
@Secured(SecurityRule.IS_AUTHENTICATED)
public class AccessController {

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
    private final boolean chatConfigured;
    private final SecureRandom random = new SecureRandom();

    public AccessController(AccessService access, AppUserRepository users, MembershipRepository memberships, InvitationRepository invitations,
                            AccessRequestRepository requests, CabinInfoRepository cabins, PasswordResetRepository resets,
                            @Value("${hyttebok.chat.api-key:}") String chatKey) {
        this.access = access;
        this.users = users;
        this.memberships = memberships;
        this.invitations = invitations;
        this.requests = requests;
        this.cabins = cabins;
        this.resets = resets;
        this.chatConfigured = !chatKey.isBlank();
    }

    @Serdeable
    public record Person(Long id, String name, String email, @Nullable String picture, @Nullable String role) {
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
    public record Accepted(Long cabinId) {
    }

    @Serdeable
    public record RoleBody(@Pattern(regexp = "OWNER|MEMBER") String role) {
    }

    @Serdeable
    public record Code(String code) {
    }

    @Serdeable
    public record ResetLink(String token, Instant expiresAt) {
    }

    /** {@code configured}: the server has an AI key (without one, the switch does nothing yet). */
    @Serdeable
    public record ChatSetting(boolean enabled, boolean configured) {
    }

    // ---- requests (made with the join code, see CabinsController) ----

    @Get("/requests")
    public List<PendingRequest> pending(Authentication auth) {

        access.requireManager(auth);
        var pending = requests.findByCabinIdAndStatusOrderByCreatedAt(CabinContext.require(), AccessRequest.PENDING);
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
        var cabin = CabinContext.require();
        var request = requests.findById(id).filter(r -> r.cabinId().equals(cabin))
            .orElseThrow(() -> new HttpStatusException(HttpStatus.NOT_FOUND, "No such request"));
        requests.update(new AccessRequest(request.id(), request.cabinId(), request.userId(), request.message(), status, request.createdAt(),
            AccessService.userId(auth), Instant.now()));
        if ("APPROVED".equals(status)) {
            join(request.userId(), cabin, Membership.MEMBER);
        }
    }

    // ---- members ----

    @Get("/members")
    public List<Person> members(Authentication auth) {

        access.requireManager(auth);
        var ids = memberships.findByCabinId(CabinContext.require()).stream().map(Membership::userId).toList();

        return people(ids).values().stream().sorted((a, b) -> a.name().compareToIgnoreCase(b.name())).toList();
    }

    @Put("/members/{userId}")
    public void changeRole(Authentication auth, Long userId, @Body @Valid RoleBody body) {

        access.requireManager(auth);
        var member = memberships.findByUserIdAndCabinId(userId, CabinContext.require())
            .orElseThrow(() -> new HttpStatusException(HttpStatus.NOT_FOUND, "Not a member"));
        memberships.update(member.withRole(body.role()));
    }

    @Delete("/members/{userId}")
    public void removeMember(Authentication auth, Long userId) {
        access.requireManager(auth);
        memberships.deleteByUserIdAndCabinId(userId, CabinContext.require());
    }

    /** System admins: give yourself owner access to the cabin of the request (e.g. one you're inspecting in admin mode). */
    @Post("/self")
    public void grantSelf(Authentication auth) {

        access.requireAdmin(auth);
        var cabin = CabinContext.require();
        memberships.findByUserIdAndCabinId(AccessService.userId(auth), cabin).ifPresentOrElse(
            m -> memberships.update(m.withRole(Membership.OWNER)),
            () -> join(AccessService.userId(auth), cabin, Membership.OWNER));
    }

    // ---- AI chat (system admins only: it costs money per question) ----

    @Get("/chat")
    public ChatSetting chat(Authentication auth) {
        access.requireAdmin(auth);
        return new ChatSetting(cabins.findChatEnabled(CabinContext.require()).orElse(false), chatConfigured);
    }

    @Put("/chat")
    public ChatSetting setChat(Authentication auth, @Body ChatSetting body) {

        access.requireAdmin(auth);
        cabins.updateChatEnabled(CabinContext.require(), body.enabled());

        return new ChatSetting(body.enabled(), chatConfigured);
    }

    // ---- join code ----

    @Get("/code")
    public Code code(Authentication auth) {
        access.requireManager(auth);
        return new Code(JoinCodes.display(cabins.findJoinCode(CabinContext.require()).orElseThrow()));
    }

    /** A new code; the old one stops working (e.g. if it was shared too widely). */
    @Post("/code")
    public Code newCode(Authentication auth) {

        access.requireManager(auth);
        var code = JoinCodes.generate();
        cabins.updateJoinCode(CabinContext.require(), code);

        return new Code(JoinCodes.display(code));
    }

    // ---- new password (no email service: the link is shared by hand) ----

    /** For members of this cabin: a one-time link to set a new password, valid for a day. */
    @Post("/users/{userId}/password-reset")
    public ResetLink passwordReset(Authentication auth, Long userId) {

        access.requireManager(auth);
        if (!memberships.existsByUserIdAndCabinId(userId, CabinContext.require())) {
            throw new HttpStatusException(HttpStatus.NOT_FOUND, "Not a member");
        }
        var reset = resets.save(new PasswordReset(token(), userId, AccessService.userId(auth), Instant.now().plus(RESET_VALID), false));

        return new ResetLink(reset.token(), reset.expiresAt());
    }

    // ---- invitations ----

    @Get("/invites")
    public List<Invite> invites(Authentication auth) {

        access.requireManager(auth);
        var now = Instant.now();
        var active = invitations.findByCabinIdAndRevokedFalseOrderByCreatedAtDesc(CabinContext.require()).stream().filter(i -> i.usable(now)).toList();
        var people = people(active.stream().map(Invitation::createdBy).filter(Objects::nonNull).toList());

        return active.stream().map(i -> new Invite(i.id(), i.token(), i.createdBy() == null || !people.containsKey(i.createdBy()) ? null : people.get(i.createdBy()).name(),
            i.expiresAt(), i.uses())).toList();
    }

    @Post("/invites")
    public Invite createInvite(Authentication auth) {

        access.requireManager(auth);
        var now = Instant.now();
        var saved = invitations.save(new Invitation(null, CabinContext.require(), token(), AccessService.userId(auth), now, now.plus(INVITE_VALID), false, 0));

        return new Invite(saved.id(), saved.token(), String.valueOf(auth.getAttributes().get("name")), saved.expiresAt(), 0);
    }

    @Delete("/invites/{id}")
    public void revokeInvite(Authentication auth, Long id) {

        access.requireManager(auth);
        var cabin = CabinContext.require();
        invitations.findById(id).filter(i -> i.cabinId().equals(cabin)).ifPresent(i -> invitations.update(
            new Invitation(i.id(), i.cabinId(), i.token(), i.createdBy(), i.createdAt(), i.expiresAt(), true, i.uses())));
    }

    /** Shown before login on the invitation page: which cabin, and who invited you. */
    @Get("/invites/{token}/info")
    @Secured(SecurityRule.IS_ANONYMOUS)
    public InviteInfo inviteInfo(String token) {

        var invite = invitations.findByToken(token).filter(i -> i.usable(Instant.now()));
        var cabinName = invite.flatMap(i -> cabins.findById(i.cabinId())).map(c -> c.name()).orElse("Hyttebok");
        var invitedBy = invite.map(Invitation::createdBy).flatMap(users::findById).map(u -> u.name() == null ? u.email() : u.name()).orElse(null);

        return new InviteInfo(invite.isPresent(), cabinName, invitedBy);
    }

    /** Joins the invitation's cabin (whatever cabin the app is showing) and says which one, so the app can go there. */
    @Post("/invites/{token}/accept")
    public Accepted accept(Authentication auth, String token) {

        var invite = invitations.findByToken(token).filter(i -> i.usable(Instant.now()))
            .orElseThrow(() -> new HttpStatusException(HttpStatus.GONE, "This invitation has expired or was revoked"));
        var userId = AccessService.userId(auth);
        if (!memberships.existsByUserIdAndCabinId(userId, invite.cabinId())) {
            join(userId, invite.cabinId(), Membership.MEMBER);
            invitations.update(new Invitation(invite.id(), invite.cabinId(), invite.token(), invite.createdBy(), invite.createdAt(), invite.expiresAt(), false,
                invite.uses() + 1));
        }
        requests.findFirstByCabinIdAndUserIdAndStatus(invite.cabinId(), userId, AccessRequest.PENDING)
            .ifPresent(r -> requests.update(new AccessRequest(r.id(), r.cabinId(), r.userId(), r.message(), "APPROVED", r.createdAt(), invite.createdBy(), Instant.now())));

        return new Accepted(invite.cabinId());
    }

    private void join(Long userId, Long cabinId, String role) {
        if (!memberships.existsByUserIdAndCabinId(userId, cabinId)) {
            memberships.save(new Membership(null, userId, cabinId, role, Instant.now()));
        }
    }

    private String token() {
        var bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private Map<Long, Person> people(List<Long> ids) {

        if (ids.isEmpty()) {
            return Map.of();
        }
        var roles = memberships.findByCabinId(CabinContext.require()).stream().collect(Collectors.toMap(Membership::userId, Membership::role));

        return users.findByIdIn(ids).stream()
            .map(u -> new Person(u.id(), u.name() == null ? u.email() : u.name(), u.email(), u.picture(), roles.get(u.id())))
            .collect(Collectors.toMap(Person::id, Function.identity()));
    }
}
