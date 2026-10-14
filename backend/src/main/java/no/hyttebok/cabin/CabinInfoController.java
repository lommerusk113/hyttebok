package no.hyttebok.cabin;

import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Put;
import jakarta.validation.Valid;

/**
 * The cabin has exactly one info row (id = 1), seeded by Flyway.
 */
@Controller("/api/cabin")
public class CabinInfoController {

    private static final long CABIN_ID = 1L;

    private final CabinInfoRepository repository;

    public CabinInfoController(CabinInfoRepository repository) {
        this.repository = repository;
    }

    @Get
    public CabinInfo get() {
        return repository.findById(CABIN_ID).orElseThrow();
    }

    @Put
    public CabinInfo update(@Body @Valid CabinInfo info) {

        // A new address means a new place: forget the old coordinates (looked up again for the weather)
        // unless coordinates were also typed in by hand.
        var current = repository.findById(CABIN_ID).orElseThrow();
        var moved = !java.util.Objects.equals(current.address(), info.address())
            && java.util.Objects.equals(current.latitude(), info.latitude()) && java.util.Objects.equals(current.longitude(), info.longitude());

        var singleton = new CabinInfo(CABIN_ID, info.name(), info.tagline(), info.description(), info.address(),
            info.wifiName(), info.wifiPassword(), info.arrivalInfo(), info.departureInfo(), info.heroPhotoId(), info.hiddenSections(),
            moved ? null : info.latitude(), moved ? null : info.longitude());

        return repository.update(singleton);
    }
}
