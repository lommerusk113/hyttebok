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
import io.micronaut.http.server.types.files.StreamedFile;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.rules.SecurityRule;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import no.hyttebok.audit.Audit;
import no.hyttebok.auth.AppUserRepository;
import no.hyttebok.cabin.CabinContext;
import no.hyttebok.cabin.CabinInfoRepository;
import no.hyttebok.chat.ChatBudget;
import no.hyttebok.mail.Links;
import no.hyttebok.mail.Mailer;
import no.hyttebok.push.Notifier;

import java.io.IOException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Who has access to the cabin of the request: members and roles, and invitation links. Owners (and admins in
 * admin mode) manage it. People get in through an invitation link; there is no other way in.
 */
@Controller("/api/access")
@Secured(SecurityRule.IS_AUTHENTICATED)
public class AccessController {

    private static final Duration INVITE_VALID = Duration.ofDays(14);
    private static final int TOKEN_BYTES = 18;
    private static final int LOG_SIZE = 50;

    private final AccessService access;
    private final AppUserRepository users;
    private final MembershipRepository memberships;
    private final InvitationRepository invitations;
    private final CabinInfoRepository cabins;
    private final Audit audit;
    private final ChatBudget budget;
    private final Notifier notifier;
    private final CabinExport exporter;
    private final Mailer mailer;
    private final Links links;
    private final boolean chatConfigured;
    private final SecureRandom random = new SecureRandom();

    public AccessController(AccessService access, AppUserRepository users, MembershipRepository memberships, InvitationRepository invitations,
                            CabinInfoRepository cabins, Audit audit, ChatBudget budget, Notifier notifier, CabinExport exporter, Mailer mailer, Links links,
                            @Value("${hyttebok.chat.api-key:}") String chatKey) {
        this.access = access;
        this.users = users;
        this.memberships = memberships;
        this.invitations = invitations;
        this.cabins = cabins;
        this.audit = audit;
        this.budget = budget;
        this.notifier = notifier;
        this.exporter = exporter;
        this.mailer = mailer;
        this.links = links;
        this.chatConfigured = !chatKey.isBlank();
    }

    @Serdeable
    public record Person(Long id, String name, String email, @Nullable String picture, @Nullable String role) {
    }

    /** {@code role}: what the link makes people (OWNER or MEMBER). {@code singleUse}: it works once. */
    @Serdeable
    public record Invite(Long id, String token, @Nullable String createdBy, Instant expiresAt, int uses, String role, boolean singleUse) {
    }

    /** {@code email}: also send the link there (then it works once). {@code lang}: the email's language. */
    @Serdeable
    public record NewInvite(@Nullable @Pattern(regexp = "OWNER|MEMBER") String role, boolean singleUse, @Nullable @Email @Size(max = 200) String email,
                            @Nullable String lang) {
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

    /**
     * {@code configured}: the server has an AI key (without one, the switch does nothing yet). {@code percentUsed}: of
     * this month's budget (the amount itself isn't shown).
     */
    @Serdeable
    public record ChatSetting(boolean enabled, boolean configured, int percentUsed) {
    }

    @Serdeable
    public record ChatSwitch(boolean enabled) {
    }

    /** For owners: how much of this month's AI budget is used, and each person's share (percent of the budget). */
    @Serdeable
    public record ChatUsage(boolean enabled, int percentUsed, List<ChatBudget.PersonShare> people) {
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
        if (Membership.MEMBER.equals(body.role())) {
            keepAnOwner(auth, member);
        }
        memberships.update(member.withRole(body.role()));
        audit.log(member.cabinId(), auth, Audit.ROLE_CHANGED, userId, body.role());
    }

    @Delete("/members/{userId}")
    public void removeMember(Authentication auth, Long userId) {

        access.requireManager(auth);
        var cabin = CabinContext.require();
        var member = memberships.findByUserIdAndCabinId(userId, cabin);
        if (member.isEmpty()) {
            return;
        }

        keepAnOwner(auth, member.get());
        memberships.deleteByUserIdAndCabinId(userId, cabin);
        audit.log(cabin, auth, Audit.MEMBER_REMOVED, userId, null);
    }

    /** Owners: everything in the cabin as a ZIP (see {@link CabinExport}). Linked as ?hytte=, since a download has no header. */
    @Get("/export")
    public StreamedFile export(Authentication auth) throws IOException {

        access.requireManager(auth);
        var cabin = CabinContext.require();
        audit.log(cabin, auth, Audit.EXPORTED, null, null);

        return exporter.export(cabin, cabins.findById(cabin).map(c -> c.name()).orElse("hytte"));
    }

    /** Owners: what changed about access to this cabin, newest first. */
    @Get("/log")
    public List<Audit.Event> log(Authentication auth) {
        access.requireManager(auth);
        return audit.forCabin(CabinContext.require(), LOG_SIZE);
    }

    /** Owners can't take away their own ownership, and a cabin always keeps at least one owner. */
    private void keepAnOwner(Authentication auth, Membership member) {

        if (!Membership.OWNER.equals(member.role())) {
            return;
        }
        if (member.userId().equals(AccessService.userId(auth))) {
            throw new HttpStatusException(HttpStatus.CONFLICT, "You can't remove yourself as owner");
        }
        if (memberships.findByCabinId(member.cabinId()).stream().filter(m -> Membership.OWNER.equals(m.role())).count() <= 1) {
            throw new HttpStatusException(HttpStatus.CONFLICT, "A cabin needs at least one owner");
        }
    }

    /** System admins: give yourself owner access to the cabin of the request (e.g. one you're inspecting in admin mode). */
    @Post("/self")
    public void grantSelf(Authentication auth) {

        access.requireAdmin(auth);
        var cabin = CabinContext.require();
        memberships.findByUserIdAndCabinId(AccessService.userId(auth), cabin).ifPresentOrElse(
            m -> memberships.update(m.withRole(Membership.OWNER)),
            () -> join(AccessService.userId(auth), cabin, Membership.OWNER));
        audit.log(cabin, auth, Audit.ADMIN_JOINED, null, null);
    }

    // ---- AI chat (system admins only: it costs money per question) ----

    @Get("/chat")
    public ChatSetting chat(Authentication auth) {

        access.requireAdmin(auth);
        var cabin = CabinContext.require();

        return new ChatSetting(cabins.findChatEnabled(cabin).orElse(false), chatConfigured, budget.percentUsed(cabin));
    }

    @Get("/chat-usage")
    public ChatUsage chatUsage(Authentication auth) {

        access.requireManager(auth);
        var cabin = CabinContext.require();

        return new ChatUsage(chatConfigured && cabins.findChatEnabled(cabin).orElse(false), budget.percentUsed(cabin), budget.byPerson(cabin));
    }

    @Put("/chat")
    public ChatSetting setChat(Authentication auth, @Body ChatSwitch body) {

        access.requireAdmin(auth);
        var cabin = CabinContext.require();
        cabins.updateChatEnabled(cabin, body.enabled());
        audit.log(cabin, auth, Audit.CHAT_CHANGED, null, body.enabled() ? "on" : "off");

        return new ChatSetting(body.enabled(), chatConfigured, budget.percentUsed(cabin));
    }

    // ---- invitations ----

    @Get("/invites")
    public List<Invite> invites(Authentication auth) {

        access.requireManager(auth);
        var now = Instant.now();
        var active = invitations.findByCabinIdAndRevokedFalseOrderByCreatedAtDesc(CabinContext.require()).stream().filter(i -> i.usable(now)).toList();
        var people = people(active.stream().map(Invitation::createdBy).filter(Objects::nonNull).toList());

        return active.stream().map(i -> new Invite(i.id(), i.token(), i.createdBy() == null || !people.containsKey(i.createdBy()) ? null : people.get(i.createdBy()).name(),
            i.expiresAt(), i.uses(), i.role(), i.maxUses() != null)).toList();
    }

    @Post("/invites")
    public Invite createInvite(Authentication auth, @Nullable @Body @Valid NewInvite body) {

        access.requireManager(auth);
        var role = body == null || body.role() == null ? Membership.MEMBER : body.role();
        var email = body == null || body.email() == null || body.email().isBlank() || !mailer.enabled() ? null : body.email().trim();
        var singleUse = email != null || body != null && body.singleUse();
        var now = Instant.now();
        var saved = invitations.save(new Invitation(null, CabinContext.require(), token(), AccessService.userId(auth), now, now.plus(INVITE_VALID), false, 0,
            role, singleUse ? 1 : null));
        audit.log(saved.cabinId(), auth, Audit.INVITE_CREATED, null, Membership.OWNER.equals(role) ? role : null);
        if (email != null) {
            mailInvite(email, String.valueOf(auth.getAttributes().get("name")), saved, "en".equals(body.lang()));
        }

        return new Invite(saved.id(), saved.token(), String.valueOf(auth.getAttributes().get("name")), saved.expiresAt(), 0, role, singleUse);
    }

    @Delete("/invites/{id}")
    public void revokeInvite(Authentication auth, Long id) {

        access.requireManager(auth);
        var cabin = CabinContext.require();
        invitations.findById(id).filter(i -> i.cabinId().equals(cabin)).ifPresent(i -> {
            invitations.update(i.withRevoked());
            audit.log(cabin, auth, Audit.INVITE_REVOKED, null, null);
        });
    }

    /** Shown before login on the invitation page: which cabin, and who invited you. */
    @Get("/invites/{token}/info")
    @Secured(SecurityRule.IS_ANONYMOUS)
    public InviteInfo inviteInfo(String token) {

        var invite = invitations.findByToken(token).filter(this::open);
        var cabinName = invite.flatMap(i -> cabins.findById(i.cabinId())).map(c -> c.name()).orElse("Hyttebok");
        var invitedBy = invite.map(Invitation::createdBy).flatMap(users::findById).map(u -> u.name() == null ? u.email() : u.name()).orElse(null);

        return new InviteInfo(invite.isPresent(), cabinName, invitedBy);
    }

    /** Joins the invitation's cabin (whatever cabin the app is showing) and says which one, so the app can go there. */
    @Post("/invites/{token}/accept")
    public Accepted accept(Authentication auth, String token) {

        var invite = invitations.findByToken(token).filter(this::open)
            .orElseThrow(() -> new HttpStatusException(HttpStatus.GONE, "This invitation has expired or was revoked"));
        var userId = AccessService.userId(auth);
        if (!memberships.existsByUserIdAndCabinId(userId, invite.cabinId())) {
            join(userId, invite.cabinId(), invite.role());
            invitations.update(invite.withUse());
            audit.log(invite.cabinId(), auth, Audit.MEMBER_JOINED, null, Membership.OWNER.equals(invite.role()) ? invite.role() : null);
            CabinContext.runAs(invite.cabinId(), () -> notifier.memberJoined(String.valueOf(auth.getAttributes().get("name")), owners(invite.cabinId(), userId)));
        }

        return new Accepted(invite.cabinId());
    }

    private void mailInvite(String email, String from, Invitation invite, boolean en) {

        var cabin = cabins.findById(invite.cabinId()).map(c -> c.name()).orElse("Hyttebok");
        var link = links.to("/invitasjon/" + invite.token());
        mailer.send(email, en ? from + " invited you to " + cabin : from + " har invitert deg til " + cabin, en
            ? "Hi!\n\n" + from + " has invited you to " + cabin + " in Hyttebok, where you book the cabin, write in the cabin book and keep the lists together.\n\n"
                + "Open the link and log in or create an account:\n\n" + link + "\n\nThe link works once, for 14 days.\n"
            : "Hei!\n\n" + from + " har invitert deg til " + cabin + " i Hyttebok, der dere booker hytta, skriver i hytteboka og holder orden på listene sammen.\n\n"
                + "Åpne lenken og logg inn eller lag en konto:\n\n" + link + "\n\nLenken virker én gang, i 14 dager.\n");
    }

    /** Not expired or revoked, and its cabin isn't deleted. */
    private boolean open(Invitation invite) {
        return invite.usable(Instant.now()) && !cabins.findDeleted(invite.cabinId()).orElse(true);
    }

    /** The owners of the cabin, except {@code except} (who just joined, maybe as an owner). */
    private Set<Long> owners(long cabinId, Long except) {
        return memberships.findByCabinId(cabinId).stream().filter(m -> Membership.OWNER.equals(m.role()) && !m.userId().equals(except)).map(Membership::userId).collect(Collectors.toSet());
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
