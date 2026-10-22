package no.hyttebok.guest;

import io.micronaut.context.annotation.Value;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.jdbc.runtime.JdbcOperations;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.http.server.types.files.SystemFile;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.transaction.annotation.Transactional;
import no.hyttebok.cabin.CabinInfoRepository;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * What a guest sees on /gjest/<token>, without logging in: the cabin's name and cover photo, and the info cards a
 * guest needs (arrival, WiFi, keys, address, departure; hidden ones stay hidden). Nothing else of the cabin.
 */
@Controller("/api/guest")
@Secured(SecurityRule.IS_ANONYMOUS)
public class GuestController {

    private static final ZoneId OSLO = ZoneId.of("Europe/Oslo");

    private final GuestLinkRepository links;
    private final CabinInfoRepository cabins;
    private final JdbcOperations jdbc;
    private final Path uploadDir;

    public GuestController(GuestLinkRepository links, CabinInfoRepository cabins, JdbcOperations jdbc, @Value("${hyttebok.upload-dir}") String uploadDir) {
        this.links = links;
        this.cabins = cabins;
        this.jdbc = jdbc;
        this.uploadDir = Path.of(uploadDir);
    }

    /** Null = not filled in, or hidden by the owners. */
    @Serdeable
    public record GuestView(String label, String cabinName, @Nullable String tagline, boolean cover, LocalDate validUntil,
                            @Nullable String arrivalInfo, @Nullable String departureInfo, @Nullable String wifiName, @Nullable String wifiPassword,
                            @Nullable String keyInfo, @Nullable String address) {
    }

    @Get("/{token}")
    public GuestView view(String token) {

        var link = open(token);
        var cabin = cabins.findById(link.cabinId()).filter(c -> !cabins.findDeleted(c.id()).orElse(false))
            .orElseThrow(() -> new HttpStatusException(HttpStatus.GONE, "The cabin is gone"));
        List<String> hidden = cabin.hiddenSections();
        var wifi = !hidden.contains("wifi");

        return new GuestView(link.label(), cabin.name(), cabin.tagline(), cabin.heroPhotoId() != null, link.validUntil(),
            hidden.contains("arrivalInfo") ? null : cabin.arrivalInfo(), hidden.contains("departureInfo") ? null : cabin.departureInfo(),
            wifi ? cabin.wifiName() : null, wifi ? cabin.wifiPassword() : null,
            hidden.contains("keyInfo") ? null : cabin.keyInfo(), hidden.contains("address") ? null : cabin.address());
    }

    /** The cover photo, the only photo a guest gets. */
    @Get("/{token}/cover")
    @Transactional(readOnly = true)
    public HttpResponse<SystemFile> cover(String token) {

        var link = open(token);
        var file = jdbc.prepareStatement("SELECT p.file_name, p.content_type FROM cabin_info c JOIN photo p ON p.id = c.hero_photo_id WHERE c.id = ?", statement -> {
            statement.setLong(1, link.cabinId());
            try (var rs = statement.executeQuery()) {
                return rs.next() ? new String[] {rs.getString(1), rs.getString(2)} : null;
            }
        });
        if (file == null) {
            throw new HttpStatusException(HttpStatus.NOT_FOUND, "No cover photo");
        }

        return HttpResponse.ok(new SystemFile(uploadDir.resolve(file[0]).toFile(), MediaType.of(file[1])))
            .header("Cache-Control", "private, max-age=86400")
            .header("X-Content-Type-Options", "nosniff")
            .header("Content-Security-Policy", "default-src 'none'; sandbox");
    }

    private GuestLink open(String token) {
        return links.findByToken(token).filter(l -> l.open(LocalDate.now(OSLO)))
            .orElseThrow(() -> new HttpStatusException(HttpStatus.GONE, "This guest link has expired or was revoked"));
    }
}
