package no.hyttebok.weather;

import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.exceptions.HttpStatusException;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Controller("/api/weather")
public class WeatherController {

    private static final int MAX_DAYS = 62;

    private final WeatherService weather;

    public WeatherController(WeatherService weather) {
        this.weather = weather;
    }

    @Get
    public DayWeather.Report get() {
        return weather.report();
    }

    /** Weather per day for a calendar view or a stay: measured, forecast, or usual for the date. */
    @Get("/days")
    public List<DayWeather> days(@QueryValue LocalDate from, @QueryValue LocalDate to) {

        if (to.isBefore(from) || ChronoUnit.DAYS.between(from, to) > MAX_DAYS) {
            throw new HttpStatusException(HttpStatus.BAD_REQUEST, "Pick at most " + MAX_DAYS + " days");
        }

        return weather.days(from, to);
    }
}
