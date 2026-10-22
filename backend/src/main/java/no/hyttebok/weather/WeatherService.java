package no.hyttebok.weather;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.json.JsonMapper;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.inject.Singleton;
import no.hyttebok.cabin.CabinContext;
import no.hyttebok.cabin.CabinInfo;
import no.hyttebok.cabin.CabinInfoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.TreeMap;

/**
 * Weather for the cabin from three sources, best first: MET (what yr.no shows) for the ~9 days it covers,
 * Open-Meteo for the rest of the 16 days (marked uncertain), and per-month averages from ~20 years of
 * Open-Meteo archive data for anything further out. Results are cached; if a source is down, the other
 * fills in, and if both are, the last good forecast is served (marked stale).
 */
@Singleton
public class WeatherService {

    private static final Logger LOG = LoggerFactory.getLogger(WeatherService.class);
    private static final ZoneId ZONE = ZoneId.of("Europe/Oslo");
    // MET asks clients to identify themselves; browsers can't set this, which is why weather goes via the backend.
    private static final String USER_AGENT = "Hyttebok/1.0 https://github.com/lommerusk113/hyttebok";
    private static final Duration FORECAST_TTL = Duration.ofMinutes(30);
    private static final int UNCERTAIN_FROM_DAYS_AHEAD = 9;
    private static final int CLIMATE_YEARS = 20;
    // The archive can lag a few days; a month is only "finished" once it has had time to fill in.
    private static final int ARCHIVE_DELAY_DAYS = 7;

    private final CabinInfoRepository cabins;
    private final JsonMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NORMAL).build();

    // Per place (rounded coordinates + height), so several cabins don't push each other's weather out.
    // ponytail: never shrinks; fine for a few dozen cabins.
    private final Map<String, Forecast> forecasts = new HashMap<>();
    private final Map<String, Climate> climates = new HashMap<>();
    private @Nullable String heightTriedFor;
    // ponytail: in memory, so a restart refetches; bounded by the months people browse (archive starts 1940).
    private final Map<String, ArchiveMonth> archive = new HashMap<>();

    private record Forecast(String key, Instant fetchedAt, List<DayWeather> days) {
    }

    private record Climate(String key, int year, List<DayWeather.MonthClimate> months, double[][] normals) {
    }

    private record ArchiveMonth(LocalDate fetchedOn, List<DayWeather> days) {
    }

    /**
     * Where to get weather for. The height matters: both MET and Open-Meteo adjust temperatures for it
     * (about 0.6° colder per 100 m), and their own terrain models are coarse along fjords and mountains.
     */
    private record Spot(double lat, double lon, @Nullable Integer elevation) {

        String key() {
            return lat + "," + lon + "," + elevation;
        }

        String met() {
            return "lat=" + lat + "&lon=" + lon + (elevation == null ? "" : "&altitude=" + elevation);
        }

        String openMeteo() {
            return "latitude=" + lat + "&longitude=" + lon + (elevation == null ? "" : "&elevation=" + elevation);
        }
    }

    public WeatherService(CabinInfoRepository cabins, JsonMapper json) {
        this.cabins = cabins;
        this.json = json;
    }

    public synchronized DayWeather.Report report() {

        var cabin = locate(cabins.findById(CabinContext.require()).orElseThrow());
        if (cabin.latitude() == null || cabin.longitude() == null) {
            return new DayWeather.Report(null, null, null, null, List.of(), List.of(), false);
        }

        var place = place(cabin);
        var stale = refreshForecast(place);
        refreshClimate(place);
        var forecast = forecasts.get(place.key());
        var climate = climates.get(place.key());

        var today = LocalDate.now(ZONE);
        var days = forecast == null ? List.<DayWeather>of() : forecast.days().stream().filter(d -> !d.date().isBefore(today)).toList();

        return new DayWeather.Report(place.lat(), place.lon(), place.elevation(), cabin.address(), days, climate == null ? List.of() : climate.months(), stale);
    }

    private static Spot place(CabinInfo cabin) {
        return new Spot(round4(cabin.latitude()), round4(cabin.longitude()), cabin.elevation() == null ? null : (int) Math.round(cabin.elevation()));
    }

    /**
     * Every day from {@code from} to {@code to}: measured weather for past days, the forecast where there is one,
     * and the usual weather for that date (from the 20-year normals) further ahead. Days without data are left out.
     */
    public synchronized List<DayWeather> days(LocalDate from, LocalDate to) {

        var report = report();
        if (report.latitude() == null || report.longitude() == null) {
            return List.of();
        }

        var place = new Spot(report.latitude(), report.longitude(), report.elevation());
        var today = LocalDate.now(ZONE);
        var result = new ArrayList<DayWeather>();

        for (var month = YearMonth.from(from); !month.atDay(1).isAfter(to) && month.atDay(1).isBefore(today); month = month.plusMonths(1)) {
            for (var day : archiveMonth(place, month, today)) {
                if (!day.date().isBefore(from) && !day.date().isAfter(to)) {
                    result.add(day);
                }
            }
        }

        var climate = climates.get(place.key());
        var forecastByDate = new HashMap<LocalDate, DayWeather>();
        report.days().forEach(d -> forecastByDate.put(d.date(), d));
        for (var date = from.isBefore(today) ? today : from; !date.isAfter(to); date = date.plusDays(1)) {
            var day = forecastByDate.get(date);
            if (day == null && climate != null) {
                var normal = climate.normals()[OpenMeteo.dayIndex(date)];
                day = Double.isNaN(normal[0]) ? null : new DayWeather(date, null, normal[0], normal[1], normal[2], "NORMAL", true);
            }
            if (day != null) {
                result.add(day);
            }
        }

        return result;
    }

    /** Measured weather for the month's days before today. Finished months are kept; the current one is refetched daily. */
    private List<DayWeather> archiveMonth(Spot place, YearMonth month, LocalDate today) {

        var cacheKey = place.key() + "/" + month;
        var cached = archive.get(cacheKey);
        if (cached != null && (cached.fetchedOn().equals(today) || month.atEndOfMonth().isBefore(cached.fetchedOn().minusDays(ARCHIVE_DELAY_DAYS)))) {
            return cached.days();
        }

        var end = month.atEndOfMonth().isBefore(today) ? month.atEndOfMonth() : today.minusDays(1);
        try {
            var days = OpenMeteo.days(get("https://archive-api.open-meteo.com/v1/archive?" + place.openMeteo()
                + "&start_date=" + month.atDay(1) + "&end_date=" + end
                + "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_sum&timezone=Europe%2FOslo", OpenMeteo.Response.class))
                .stream().map(d -> d.withSource("ARCHIVE", false)).toList();
            archive.put(cacheKey, new ArchiveMonth(today, days));
            return days;
        } catch (Exception e) {
            LOG.warn("Open-Meteo archive for {} failed: {}", month, e.toString());
            return cached == null ? List.of() : cached.days();
        }
    }

    /** @return true if serving an older forecast because both sources failed */
    private boolean refreshForecast(Spot place) {

        var forecast = forecasts.get(place.key());
        if (forecast != null && forecast.fetchedAt().plus(FORECAST_TTL).isAfter(Instant.now())) {
            return false;
        }

        List<DayWeather> met = null;
        List<DayWeather> openMeteo = null;
        try {
            met = MetForecast.days(get("https://api.met.no/weatherapi/locationforecast/2.0/compact?" + place.met(), MetForecast.Response.class), ZONE);
        } catch (Exception e) {
            LOG.warn("MET forecast failed: {}", e.toString());
        }
        try {
            openMeteo = OpenMeteo.days(get("https://api.open-meteo.com/v1/forecast?" + place.openMeteo()
                + "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_sum&timezone=Europe%2FOslo&forecast_days=16", OpenMeteo.Response.class));
        } catch (Exception e) {
            LOG.warn("Open-Meteo forecast failed: {}", e.toString());
        }

        if (met == null && openMeteo == null) {
            return forecast != null;
        }

        forecasts.put(place.key(), new Forecast(place.key(), Instant.now(), merge(met, openMeteo)));
        return false;
    }

    /** MET where it has the day; Open-Meteo after that. Far-ahead days are flagged uncertain whatever the source. */
    static List<DayWeather> merge(@Nullable List<DayWeather> met, @Nullable List<DayWeather> openMeteo) {

        var today = LocalDate.now(ZONE);
        var byDate = new TreeMap<LocalDate, DayWeather>();
        for (var list : List.of(met == null ? List.<DayWeather>of() : met, openMeteo == null ? List.<DayWeather>of() : openMeteo)) {
            for (var day : list) {
                var uncertain = ChronoUnit.DAYS.between(today, day.date()) >= UNCERTAIN_FROM_DAYS_AHEAD;
                byDate.putIfAbsent(day.date(), day.withSource(day.source(), uncertain));
            }
        }

        return new ArrayList<>(byDate.values());
    }

    private void refreshClimate(Spot place) {

        var year = LocalDate.now(ZONE).getYear();
        var climate = climates.get(place.key());
        if (climate != null && climate.year() == year) {
            return;
        }
        try {
            var archive = get("https://archive-api.open-meteo.com/v1/archive?" + place.openMeteo()
                + "&start_date=" + (year - CLIMATE_YEARS) + "-01-01&end_date=" + (year - 1) + "-12-31"
                + "&daily=temperature_2m_max,temperature_2m_min,precipitation_sum&timezone=Europe%2FOslo", OpenMeteo.Response.class);
            climates.put(place.key(), new Climate(place.key(), year, OpenMeteo.climate(archive), OpenMeteo.normals(archive)));
        } catch (Exception e) {
            LOG.warn("Open-Meteo archive failed: {}", e.toString()); // keep the previous averages, if any
        }
    }

    // ---- location ----

    @Serdeable
    record AddressSearch(List<Address> adresser) {
    }

    @Serdeable
    record Address(LatLon representasjonspunkt) {
    }

    @Serdeable
    record LatLon(double lat, double lon) {
    }

    @Serdeable
    record PlaceSearch(List<Place> navn) {
    }

    @Serdeable
    record Place(NorthEast representasjonspunkt) {
    }

    // Kartverket's place-name API uses Norwegian keys: nord = latitude, øst = longitude.
    @Serdeable
    record NorthEast(double nord, double øst) {
    }

    @Serdeable
    record HeightSearch(List<Height> punkter) {
    }

    @Serdeable
    record Height(@Nullable Double z) {
    }

    /**
     * Fills in coordinates from the address the first time (Kartverket: exact address, else place name),
     * and the ground height there (Kartverket's terrain model).
     */
    private CabinInfo locate(CabinInfo cabin) {

        var lat = cabin.latitude();
        var lon = cabin.longitude();
        if (lat == null || lon == null) {
            if (cabin.address() == null || cabin.address().isBlank()) {
                return cabin;
            }
            try {
                var point = geocode(cabin.address());
                if (point != null) {
                    lat = point[0];
                    lon = point[1];
                }
            } catch (Exception e) {
                LOG.warn("Geocoding '{}' failed: {}", cabin.address(), e.toString());
            }
            if (lat == null || lon == null) {
                return cabin;
            }
        }

        var elevation = cabin.elevation();
        var point = lat + "," + lon;
        if (elevation == null && !point.equals(heightTriedFor)) {
            heightTriedFor = point; // once per place, so an outage (or a spot outside Norway) doesn't mean a lookup per request
            try {
                elevation = height(lat, lon);
            } catch (Exception e) {
                LOG.warn("Height lookup failed: {}", e.toString());
            }
        }
        if (lat.equals(cabin.latitude()) && lon.equals(cabin.longitude()) && java.util.Objects.equals(elevation, cabin.elevation())) {
            return cabin;
        }

        return cabins.update(new CabinInfo(cabin.id(), cabin.name(), cabin.tagline(), cabin.description(), cabin.address(), cabin.wifiName(),
            cabin.wifiPassword(), cabin.arrivalInfo(), cabin.departureInfo(), cabin.heroPhotoId(), cabin.hiddenSections(), lat, lon, elevation,
            cabin.locationTyped(), cabin.keyInfo()));
    }

    /** Metres above sea level, or null outside Kartverket's coverage. */
    @Nullable
    private Double height(double lat, double lon) throws IOException, InterruptedException {

        var result = get("https://ws.geonorge.no/hoydedata/v1/punkt?koordsys=4258&geojson=false&nord=" + lat + "&ost=" + lon, HeightSearch.class);

        return result.punkter() == null || result.punkter().isEmpty() ? null : result.punkter().get(0).z();
    }

    @Nullable
    private double[] geocode(String address) throws IOException, InterruptedException {

        var text = address.replace('\n', ' ').trim();
        var exact = get("https://ws.geonorge.no/adresser/v1/sok?treffPerSide=1&sok=" + encode(text), AddressSearch.class);
        if (exact.adresser() != null && !exact.adresser().isEmpty()) {
            var p = exact.adresser().get(0).representasjonspunkt();
            return new double[] {p.lat(), p.lon()};
        }

        // Not a known street address: try the place name, e.g. "Syvde" from "Storvegen 1, 6140 Syvde".
        var words = text.replaceAll("[0-9,]", " ").trim().split("\\s+");
        for (var candidate : List.of(text, words[words.length - 1])) {
            var place = get("https://api.kartverket.no/stedsnavn/v1/navn?treffPerSide=1&sok=" + encode(candidate), PlaceSearch.class);
            if (place.navn() != null && !place.navn().isEmpty()) {
                var p = place.navn().get(0).representasjonspunkt();
                return new double[] {p.nord(), p.øst()};
            }
        }

        return null;
    }

    private <T> T get(String url, Class<T> type) throws IOException, InterruptedException {

        var request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20)).header("User-Agent", USER_AGENT).GET().build();
        var response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) {
            throw new IOException("HTTP " + response.statusCode() + " from " + URI.create(url).getHost());
        }

        return json.readValue(response.body(), type);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    // MET asks for at most 4 decimals (≈10 m); more just defeats caching.
    private static double round4(double value) {
        return Double.parseDouble(String.format(Locale.ROOT, "%.4f", value));
    }

}
