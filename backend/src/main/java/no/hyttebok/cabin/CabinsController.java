package no.hyttebok.cabin;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.rules.SecurityRule;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import no.hyttebok.access.AccessRequest;
import no.hyttebok.access.AccessRequestRepository;
import no.hyttebok.access.AccessService;
import no.hyttebok.access.Membership;
import no.hyttebok.access.MembershipRepository;
import no.hyttebok.push.Notifier;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * "Mine hytter": the cabins you belong to, making a new one, and asking for access with a cabin's join code.
 * Nobody can list or search other people's cabins; the code is the only way to find one (admins in admin mode
 * excepted, for support).
 */
@Controller("/api/cabins")
@Secured(SecurityRule.IS_AUTHENTICATED)
public class CabinsController {

    private static final int MAX_WRONG_CODES = 10;
    private static final Duration WRONG_CODE_WINDOW = Duration.ofHours(1);

    private final CabinInfoRepository cabins;
    private final MembershipRepository memberships;
    private final AccessRequestRepository requests;
    private final AccessService access;
    private final Notifier notifier;
    // ponytail: in memory, per user; a restart forgets it. Enough to stop guessing codes by hand or script.
    private final Map<Long, WrongCodes> wrongCodes = new ConcurrentHashMap<>();

    private record WrongCodes(int count, Instant since) {
    }

    public CabinsController(CabinInfoRepository cabins, MembershipRepository memberships, AccessRequestRepository requests,
                            AccessService access, Notifier notifier) {
        this.cabins = cabins;
        this.memberships = memberships;
        this.requests = requests;
        this.access = access;
        this.notifier = notifier;
    }

    @Serdeable
    public record Cabin(Long id, String name, @Nullable String tagline, @Nullable Long heroPhotoId, @Nullable String role) {
    }

    @Serdeable
    public record NewCabin(@NotBlank @Size(max = 80) String name) {
    }

    @Serdeable
    public record JoinRequest(@NotBlank @Size(max = 20) String code, @Nullable @Size(max = 500) String message) {
    }

    /** {@code status}: MEMBER (already in: go there), PENDING (asked before) or REQUESTED (owners told now). */
    @Serdeable
    public record JoinResult(Long cabinId, String cabinName, String status) {
    }

    @Serdeable
    public record Waiting(String cabinName, Instant since) {
    }

    /** Your cabins with your role. Admins in admin mode see every cabin. */
    @Get
    public List<Cabin> mine(@Nullable Authentication auth) {

        if (auth == null) {
            return cabins.listOrderByName().stream().map(c -> summary(c, Membership.OWNER)).toList(); // login switched off: one shared cabin
        }
        var roles = memberships.findByUserId(AccessService.userId(auth)).stream().collect(Collectors.toMap(Membership::cabinId, Membership::role));
        var list = access.adminMode(auth) ? cabins.listOrderByName() : cabins.findByIdInOrderByName(roles.keySet());
        if (roles.isEmpty() && !access.adminMode(auth)) {
            return List.of();
        }

        return list.stream().map(c -> summary(c, roles.get(c.id()))).toList();
    }

    /** A new cabin, with you as its owner. */
    @Post
    public Cabin create(Authentication auth, @Body @Valid NewCabin body) {

        var cabin = cabins.save(new CabinInfo(null, body.name().trim(), null, null, null, null, null, null, null, null, List.of(), null, null, null, false));
        cabins.updateJoinCode(cabin.id(), JoinCodes.generate());
        memberships.save(new Membership(null, AccessService.userId(auth), cabin.id(), Membership.OWNER, Instant.now()));

        return summary(cabin, Membership.OWNER);
    }

    /** "Har du en kode?": find the cabin by its join code and ask its owners for access. */
    @Post("/join")
    public JoinResult join(Authentication auth, @Body @Valid JoinRequest body) {

        var userId = AccessService.userId(auth);
        var now = Instant.now();
        var wrong = wrongCodes.get(userId);
        if (wrong != null && wrong.since().plus(WRONG_CODE_WINDOW).isBefore(now)) {
            wrongCodes.remove(userId);
            wrong = null;
        }
        if (wrong != null && wrong.count() >= MAX_WRONG_CODES) {
            throw new HttpStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many wrong codes; try again later");
        }

        var cabinId = cabins.findIdByJoinCode(JoinCodes.normalize(body.code()));
        if (cabinId.isEmpty()) {
            wrongCodes.merge(userId, new WrongCodes(1, now), (old, one) -> new WrongCodes(old.count() + 1, old.since()));
            throw new HttpStatusException(HttpStatus.NOT_FOUND, "No cabin with that code");
        }
        var cabin = cabins.findById(cabinId.get()).orElseThrow();

        if (memberships.existsByUserIdAndCabinId(userId, cabin.id())) {
            return new JoinResult(cabin.id(), cabin.name(), "MEMBER");
        }
        if (requests.findFirstByCabinIdAndUserIdAndStatus(cabin.id(), userId, AccessRequest.PENDING).isPresent()) {
            return new JoinResult(cabin.id(), cabin.name(), "PENDING");
        }
        var message = body.message() == null || body.message().isBlank() ? null : body.message().trim();
        var saved = requests.save(new AccessRequest(null, cabin.id(), userId, message, AccessRequest.PENDING, now, null, null));
        notifier.accessRequested(cabin.id(), saved.id(), userId, String.valueOf(auth.getAttributes().get("name")), message);

        return new JoinResult(cabin.id(), cabin.name(), "REQUESTED");
    }

    /** Your requests still waiting for an answer (shown on "Mine hytter"; only the cabin's name, which you typed the code for). */
    @Get("/waiting")
    public List<Waiting> waiting(Authentication auth) {

        var pending = requests.findByUserIdAndStatus(AccessService.userId(auth), AccessRequest.PENDING);
        var names = cabins.findByIdInOrderByName(pending.stream().map(AccessRequest::cabinId).toList()).stream()
            .collect(Collectors.toMap(CabinInfo::id, Function.identity()));

        return pending.stream().filter(r -> names.containsKey(r.cabinId())).map(r -> new Waiting(names.get(r.cabinId()).name(), r.createdAt())).toList();
    }

    private static Cabin summary(CabinInfo cabin, @Nullable String role) {
        return new Cabin(cabin.id(), cabin.name(), cabin.tagline(), cabin.heroPhotoId(), role);
    }
}
