package no.hyttebok.weather;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * MET Locationforecast 2.0 "compact" (the data behind yr.no), boiled down to one entry per local day.
 * The series is hourly for ~2.5 days, then 6-hourly; precipitation is summed without counting overlaps.
 */
final class MetForecast {

    private MetForecast() {
    }

    @Serdeable
    record Response(Properties properties) {
    }

    @Serdeable
    record Properties(List<Step> timeseries) {
    }

    @Serdeable
    record Step(String time, Data data) {
    }

    @Serdeable
    record Data(Point instant, @Nullable Period next_1_hours, @Nullable Period next_6_hours, @Nullable Period next_12_hours) {
    }

    @Serdeable
    record Point(Map<String, Double> details) {
    }

    @Serdeable
    record Period(@Nullable Summary summary, @Nullable Map<String, Double> details) {

        double precipitation() {
            return details == null ? 0 : details.getOrDefault("precipitation_amount", 0d);
        }

        @Nullable
        String symbol() {
            return summary == null ? null : summary.symbol_code();
        }
    }

    @Serdeable
    record Summary(String symbol_code) {
    }

    private static final class Day {
        double max = Double.NEGATIVE_INFINITY;
        double min = Double.POSITIVE_INFINITY;
        double precipitation;
        @Nullable String daytimeSymbol;
        @Nullable String anySymbol;
    }

    static List<DayWeather> days(Response response, ZoneId zone) {

        var byDate = new TreeMap<LocalDate, Day>();
        Instant coveredUntil = Instant.MIN;

        for (var step : response.properties().timeseries()) {
            var time = Instant.parse(step.time());
            var local = ZonedDateTime.ofInstant(time, zone);
            var day = byDate.computeIfAbsent(local.toLocalDate(), d -> new Day());
            var data = step.data();

            var temperature = data.instant().details().get("air_temperature");
            if (temperature != null) {
                day.max = Math.max(day.max, temperature);
                day.min = Math.min(day.min, temperature);
            }

            // Hourly amounts while they exist, then 6-hour blocks; skip anything already counted.
            if (!time.isBefore(coveredUntil)) {
                if (data.next_1_hours() != null) {
                    day.precipitation += data.next_1_hours().precipitation();
                    coveredUntil = time.plusSeconds(3600);
                } else if (data.next_6_hours() != null) {
                    day.precipitation += data.next_6_hours().precipitation();
                    coveredUntil = time.plusSeconds(6 * 3600);
                }
            }

            // The day's icon: the 12-hour summary from the morning, else the 6-hour one around midday.
            var hour = local.getHour();
            if (day.daytimeSymbol == null && data.next_12_hours() != null && hour >= 6 && hour <= 8) {
                day.daytimeSymbol = data.next_12_hours().symbol();
            }
            if (day.daytimeSymbol == null && data.next_6_hours() != null && hour >= 11 && hour <= 13) {
                day.daytimeSymbol = data.next_6_hours().symbol();
            }
            if (day.anySymbol == null) {
                day.anySymbol = firstNonNull(data.next_1_hours(), data.next_6_hours(), data.next_12_hours());
            }
        }

        var result = new ArrayList<DayWeather>();
        var today = LocalDate.now(zone);
        for (var entry : byDate.entrySet()) {
            var day = entry.getValue();
            // The last date usually has only a night step: not a usable day forecast.
            var symbol = day.daytimeSymbol != null ? day.daytimeSymbol : entry.getKey().equals(today) ? day.anySymbol : null;
            if (symbol != null && day.max != Double.NEGATIVE_INFINITY) {
                result.add(new DayWeather(entry.getKey(), symbol, round(day.max), round(day.min), round(day.precipitation), "MET", false));
            }
        }

        return result;
    }

    @Nullable
    private static String firstNonNull(@Nullable Period... periods) {
        for (var period : periods) {
            if (period != null && period.symbol() != null) {
                return period.symbol();
            }
        }
        return null;
    }

    static double round(double value) {
        return Math.round(value * 10) / 10.0;
    }
}
