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

        var singleton = new CabinInfo(CABIN_ID, info.name(), info.tagline(), info.description(), info.address(),
            info.wifiName(), info.wifiPassword(), info.arrivalInfo(), info.departureInfo(), info.heroPhotoId(), info.hiddenSections());

        return repository.update(singleton);
    }
}
