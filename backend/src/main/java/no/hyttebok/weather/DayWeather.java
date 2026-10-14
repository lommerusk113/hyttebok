package no.hyttebok.weather;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

import java.time.LocalDate;

/**
 * One day's weather, whichever source it came from: measured (ARCHIVE), forecast (MET, OPEN_METEO) or
 * usual for the date (NORMAL, no symbol). {@code symbol} uses MET's icon names (e.g. "partlycloudy_day");
 * {@code uncertain} marks days far enough ahead to show faded.
 */
@Serdeable
public record DayWeather(LocalDate date, @Nullable String symbol, double maxTemp, double minTemp, double precipitation, String source, boolean uncertain) {

    public DayWeather withSource(String newSource, boolean isUncertain) {
        return new DayWeather(date, symbol, maxTemp, minTemp, precipitation, newSource, isUncertain);
    }

    @Serdeable
    public record MonthClimate(int month, double maxTemp, double minTemp, double precipitation, int rainChancePercent) {
    }

    @Serdeable
    public record Report(@Nullable Double latitude, @Nullable Double longitude, @Nullable String place,
                         java.util.List<DayWeather> days, java.util.List<MonthClimate> climate, boolean stale) {
    }
}
