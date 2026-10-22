package no.hyttebok.cabin;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpResponse;
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

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * "Mine hytter": the cabins you belong to, and making a new one. Nobody can list or search other people's cabins;
 * an invitation link is the only way into one (admins in admin mode excepted, for support). Owners can delete a cabin; it is only marked deleted, and system admins can bring it back.
 */
@Controller("/api/cabins")
@Secured(SecurityRule.IS_AUTHENTICATED)
public class CabinsController {

    private final CabinInfoRepository cabins;
    private final MembershipRepository memberships;
    private final AccessService access;
    private final DemoCabin demo;
    private final Audit audit;

    public CabinsController(CabinInfoRepository cabins, MembershipRepository memberships, AccessService access, DemoCabin demo, Audit audit) {
        this.cabins = cabins;
        this.memberships = memberships;
        this.access = access;
        this.demo = demo;
        this.audit = audit;
    }

    @Serdeable
    public record Cabin(Long id, String name, @Nullable String tagline, @Nullable Long heroPhotoId, @Nullable String role, boolean deleted) {
    }

    @Serdeable
    public record NewCabin(@NotBlank @Size(max = 80) String name) {
    }

    @Serdeable
    public record Example(Long id, String name) {
    }

    @Serdeable
    public record ExampleSwitch(boolean on) {
    }

    /** {@code lang}: the app's language, so the test cabin's texts match it. */
    @Serdeable
    public record DemoRequest(@Nullable String lang) {
    }

    /** Your cabins with your role (admins find every cabin in the admin console). */
    @Get
    public List<Cabin> mine(@Nullable Authentication auth) {

        var deleted = Set.copyOf(cabins.findDeletedIds());
        if (auth == null) {
            return cabins.listOrderByName().stream().filter(c -> !deleted.contains(c.id())) // login switched off: one shared cabin
                .map(c -> summary(c, Membership.OWNER, false)).toList();
        }
        var roles = memberships.findByUserId(AccessService.userId(auth)).stream().collect(Collectors.toMap(Membership::cabinId, Membership::role));
        roles.keySet().removeAll(deleted);
        if (roles.isEmpty()) {
            return List.of();
        }

        return cabins.findByIdInOrderByName(roles.keySet()).stream().map(c -> summary(c, roles.get(c.id()), false)).toList();
    }

    /** A new cabin, with you as its owner. */
    @Post
    public Cabin create(Authentication auth, @Body @Valid NewCabin body) {

        var cabin = cabins.save(new CabinInfo(null, body.name().trim(), null, null, null, null, null, null, null, null, List.of(), null, null, null, false, null));
        memberships.save(new Membership(null, AccessService.userId(auth), cabin.id(), Membership.OWNER, Instant.now()));
        audit.log(cabin.id(), auth, Audit.CABIN_CREATED, null, null);

        return summary(cabin, Membership.OWNER, false);
    }

    /** Owners (and admins in admin mode): marks the cabin deleted. Its members lose access; the data stays. */
    @Delete("/{id}")
    public void delete(Authentication auth, Long id) {

        if (!access.adminMode(auth) && !Membership.OWNER.equals(access.role(auth, id))) {
            throw new HttpStatusException(HttpStatus.FORBIDDEN, "Only owners can delete the cabin");
        }
        cabins.updateDeleted(id, Instant.now(), AccessService.userId(auth));
        audit.log(id, auth, Audit.CABIN_DELETED, null, null);
    }

    /** The example cabin for the landing page ("Se en eksempelhytte"), if a system admin picked one (else 204, no content). */
    @Get("/example")
    @Secured(SecurityRule.IS_ANONYMOUS)
    public HttpResponse<Example> example() {
        return cabins.findExampleId().flatMap(cabins::findById).map(c -> HttpResponse.ok(new Example(c.id(), c.name())))
            .orElse(HttpResponse.noContent());
    }

    /** System admins: makes this cabin the example anyone can read without logging in (or stops it being one). */
    @Put("/{id}/example")
    @Transactional
    public void setExample(Authentication auth, Long id, @Body ExampleSwitch body) {

        access.requireAdmin(auth);
        var isExample = cabins.findExampleId().filter(id::equals).isPresent();
        if (body.on() != isExample) {
            cabins.clearExample();
        }
        if (body.on() && !isExample) {
            cabins.updateExample(id);
        }
    }

    /** System admins: brings a deleted cabin back, as it was. */
    @Post("/{id}/restore")
    public void restore(Authentication auth, Long id) {

        access.requireAdmin(auth);
        cabins.updateDeleted(id, null, null);
        audit.log(id, auth, Audit.CABIN_RESTORED, null, null);
    }

    /** System admins: a new cabin filled with example data from the last couple of years, in the app's language, for trying things out. */
    @Post("/demo")
    public Cabin createDemo(Authentication auth, @Body DemoRequest body) {

        access.requireAdmin(auth);
        var cabin = demo.create(AccessService.userId(auth), String.valueOf(auth.getAttributes().get("name")), body.lang());
        audit.log(cabin.id(), auth, Audit.CABIN_CREATED, null, "demo");

        return summary(cabin, Membership.OWNER, false);
    }

    private static Cabin summary(CabinInfo cabin, @Nullable String role, boolean deleted) {
        return new Cabin(cabin.id(), cabin.name(), cabin.tagline(), cabin.heroPhotoId(), role, deleted);
    }
}
