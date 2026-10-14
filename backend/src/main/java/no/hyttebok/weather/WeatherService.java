package no.hyttebok.weather;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.json.JsonMapper;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.inject.Singleton;
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
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
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
    private static final long CABIN_ID = 1L;
    // MET asks clients to identify themselves; browsers can't set this, which is why weather goes via the backend.
    private static final String USER_AGENT = "Hyttebok/1.0 https://github.com/lommerusk113/hyttebok";
    private static final Duration FORECAST_TTL = Duration.ofMinutes(30);
    private static final int UNCERTAIN_FROM_DAYS_AHEAD = 9;
    private static final int CLIMATE_YEARS = 20;

    private final CabinInfoRepository cabins;
    private final JsonMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NORMAL).build();

    private @Nullable Forecast forecast;
    private @Nullable Climate climate;

    private record Forecast(String key, Instant fetchedAt, List<DayWeather> days) {
    }

    private record Climate(String key, int year, List<DayWeather.MonthClimate> months) {
    }

    public WeatherService(CabinInfoRepository cabins, JsonMapper json) {
        this.cabins = cabins;
        this.json = json;
    }

    public synchronized DayWeather.Report report() {

        var cabin = locate(cabins.findById(CABIN_ID).orElseThrow());
        if (cabin.latitude() == null || cabin.longitude() == null) {
            return new DayWeather.Report(null, null, null, List.of(), List.of(), false);
        }

        var lat = round4(cabin.latitude());
        var lon = round4(cabin.longitude());
        var key = lat + "," + lon;
        var stale = refreshForecast(key, lat, lon);
        refreshClimate(key, lat, lon);

        var today = LocalDate.now(ZONE);
        var days = forecast == null ? List.<DayWeather>of() : forecast.days().stream().filter(d -> !d.date().isBefore(today)).toList();

        return new DayWeather.Report(lat, lon, null, days, climate == null ? List.of() : climate.months(), stale);
    }

    /** @return true if serving an older forecast because both sources failed */
    private boolean refreshForecast(String key, double lat, double lon) {

        if (forecast != null && forecast.key().equals(key) && forecast.fetchedAt().plus(FORECAST_TTL).isAfter(Instant.now())) {
            return false;
        }

        List<DayWeather> met = null;
        List<DayWeather> openMeteo = null;
        try {
            met = MetForecast.days(get("https://api.met.no/weatherapi/locationforecast/2.0/compact?lat=" + lat + "&lon=" + lon, MetForecast.Response.class), ZONE);
        } catch (Exception e) {
            LOG.warn("MET forecast failed: {}", e.toString());
        }
        try {
            openMeteo = OpenMeteo.days(get("https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon
                + "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_sum&timezone=Europe%2FOslo&forecast_days=16", OpenMeteo.Response.class));
        } catch (Exception e) {
            LOG.warn("Open-Meteo forecast failed: {}", e.toString());
        }

        if (met == null && openMeteo == null) {
            return forecast != null;
        }

        forecast = new Forecast(key, Instant.now(), merge(met, openMeteo));
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

    private void refreshClimate(String key, double lat, double lon) {

        var year = LocalDate.now(ZONE).getYear();
        if (climate != null && climate.key().equals(key) && climate.year() == year) {
            return;
        }
        try {
            var archive = get("https://archive-api.open-meteo.com/v1/archive?latitude=" + lat + "&longitude=" + lon
                + "&start_date=" + (year - CLIMATE_YEARS) + "-01-01&end_date=" + (year - 1) + "-12-31"
                + "&daily=temperature_2m_max,temperature_2m_min,precipitation_sum&timezone=Europe%2FOslo", OpenMeteo.Response.class);
            climate = new Climate(key, year, OpenMeteo.climate(archive));
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

    /** Fills in coordinates from the address the first time (Kartverket: exact address, else place name). */
    private CabinInfo locate(CabinInfo cabin) {

        if (cabin.latitude() != null || cabin.address() == null || cabin.address().isBlank()) {
            return cabin;
        }

        double[] point = null;
        try {
            point = geocode(cabin.address());
        } catch (Exception e) {
            LOG.warn("Geocoding '{}' failed: {}", cabin.address(), e.toString());
        }
        if (point == null) {
            return cabin;
        }

        return cabins.update(new CabinInfo(cabin.id(), cabin.name(), cabin.tagline(), cabin.description(), cabin.address(), cabin.wifiName(),
            cabin.wifiPassword(), cabin.arrivalInfo(), cabin.departureInfo(), cabin.heroPhotoId(), cabin.hiddenSections(), point[0], point[1]));
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
