package no.hyttebok.guest;

import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.rules.SecurityRule;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import no.hyttebok.access.AccessService;
import no.hyttebok.audit.Audit;
import no.hyttebok.cabin.CabinContext;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Base64;
import java.util.List;

/** Owners: guest links for the cabin of the request ("Tilgang"). */
@Controller("/api/guest-links")
@Secured(SecurityRule.IS_AUTHENTICATED)
public class GuestLinkController {

    private static final ZoneId OSLO = ZoneId.of("Europe/Oslo");
    private static final int TOKEN_BYTES = 18;
    private static final int MAX_DAYS = 366;

    private final GuestLinkRepository links;
    private final AccessService access;
    private final Audit audit;
    private final SecureRandom random = new SecureRandom();

    public GuestLinkController(GuestLinkRepository links, AccessService access, Audit audit) {
        this.links = links;
        this.access = access;
        this.audit = audit;
    }

    /** {@code label}: who it's for ("Familien Hansen"). {@code validUntil}: the last day it works. */
    @Serdeable
    public record NewGuestLink(@NotBlank @Size(max = 80) String label, @NotNull LocalDate validUntil) {
    }

    @Serdeable
    public record Link(Long id, String token, String label, LocalDate validUntil, boolean expired) {
    }

    @Get
    public List<Link> list(Authentication auth) {

        access.requireManager(auth);
        var today = LocalDate.now(OSLO);

        return links.findByCabinIdAndRevokedFalseOrderByValidUntil(CabinContext.require()).stream()
            .map(l -> new Link(l.id(), l.token(), l.label(), l.validUntil(), !l.open(today))).toList();
    }

    @Post
    public Link create(Authentication auth, @Body @Valid NewGuestLink body) {

        access.requireManager(auth);
        var today = LocalDate.now(OSLO);
        if (body.validUntil().isBefore(today) || body.validUntil().isAfter(today.plusDays(MAX_DAYS))) {
            throw new HttpStatusException(HttpStatus.BAD_REQUEST, "The last day must be from today and within a year");
        }
        var bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        var saved = links.save(new GuestLink(null, CabinContext.require(), Base64.getUrlEncoder().withoutPadding().encodeToString(bytes), body.label().trim(),
            body.validUntil(), false, AccessService.userId(auth), null));
        audit.log(saved.cabinId(), auth, Audit.GUEST_LINK_CREATED, null, saved.label());

        return new Link(saved.id(), saved.token(), saved.label(), saved.validUntil(), false);
    }

    @Delete("/{id}")
    public void revoke(Authentication auth, Long id) {

        access.requireManager(auth);
        var cabin = CabinContext.require();
        links.findById(id).filter(l -> l.cabinId().equals(cabin)).ifPresent(l -> {
            links.update(l.withRevoked());
            audit.log(cabin, auth, Audit.GUEST_LINK_REVOKED, null, l.label());
        });
    }
}
