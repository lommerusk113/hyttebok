package no.hyttebok.cabin;

import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Put;
import jakarta.validation.Valid;
import no.hyttebok.access.MembershipRepository;
import no.hyttebok.auth.AppUserRepository;

import java.util.List;

/** The info (name, routines, address, ...) of the cabin the app is showing (X-Hytte). Members only (MemberRule). */
@Controller("/api/cabin")
public class CabinInfoController {


    private final CabinInfoRepository repository;
    private final MembershipRepository memberships;
    private final AppUserRepository users;

    public CabinInfoController(CabinInfoRepository repository, MembershipRepository memberships, AppUserRepository users) {
        this.repository = repository;
        this.memberships = memberships;
        this.users = users;
    }

    /**
     * First names of the cabin's members, for "who was there" when something is done. Members only (MemberRule);
     * with login switched off there are no accounts, so the list is empty and names are typed.
     */
    @Get("/people")
    public List<String> people() {

        var ids = memberships.findByCabinId(CabinContext.require()).stream().map(m -> m.userId()).toList();
        if (ids.isEmpty()) {
            return List.of();
        }

        return users.findByIdIn(ids).stream().map(u -> u.name() == null ? u.email() : u.name().trim().split("\\s+")[0])
            .distinct().sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    @Get
    public CabinInfo get() {
        return repository.findById(CabinContext.require()).orElseThrow();
    }

    @Put
    public CabinInfo update(@Body @Valid CabinInfo info) {

        // Coordinates found from the address belong to that address: a new address means they (and the height)
        // are looked up again for the weather. Coordinates typed in by hand stay. A height typed in this save is
        // kept either way: the address can be the nearest village while the height is the cabin's own.
        var cabinId = CabinContext.require();
        var current = repository.findById(cabinId).orElseThrow();
        var moved = !java.util.Objects.equals(current.address(), info.address()) && !info.locationTyped();
        var heightTyped = !java.util.Objects.equals(current.elevation(), info.elevation());

        var cabin = new CabinInfo(cabinId, info.name(), info.tagline(), info.description(), info.address(),
            info.wifiName(), info.wifiPassword(), info.arrivalInfo(), info.departureInfo(), info.heroPhotoId(), info.hiddenSections(),
            moved ? null : info.latitude(), moved ? null : info.longitude(), moved && !heightTyped ? null : info.elevation(),
            info.locationTyped() && info.latitude() != null);

        return repository.update(cabin);
    }
}
