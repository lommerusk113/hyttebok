package no.hyttebok.weather;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

import java.time.LocalDate;

/**
 * One day's forecast, whichever source it came from. {@code symbol} uses MET's icon names
 * (e.g. "partlycloudy_day"); {@code uncertain} marks days far enough ahead to show faded.
 */
@Serdeable
public record DayWeather(LocalDate date, String symbol, double maxTemp, double minTemp, double precipitation, String source, boolean uncertain) {

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
