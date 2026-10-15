package no.hyttebok.cabin;

import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Put;
import jakarta.validation.Valid;

/** The info (name, routines, address, ...) of the cabin the app is showing (X-Hytte). Members only (MemberRule). */
@Controller("/api/cabin")
public class CabinInfoController {


    private final CabinInfoRepository repository;

    public CabinInfoController(CabinInfoRepository repository) {
        this.repository = repository;
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
