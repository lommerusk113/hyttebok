package no.hyttebok.weather;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Open-Meteo daily forecast (days MET doesn't cover) and its archive (for usual weather per month). */
final class OpenMeteo {

    private static final double RAIN_DAY_MM = 1.0;

    private OpenMeteo() {
    }

    @Serdeable
    record Response(Daily daily) {
    }

    @Serdeable
    record Daily(List<String> time,
                 @Nullable List<Integer> weather_code,
                 List<Double> temperature_2m_max,
                 List<Double> temperature_2m_min,
                 List<Double> precipitation_sum) {
    }

    // WMO weather codes → MET icon names, so every day uses the same icon set.
    private static final Map<Integer, String> SYMBOLS = Map.ofEntries(
        Map.entry(0, "clearsky_day"), Map.entry(1, "fair_day"), Map.entry(2, "partlycloudy_day"), Map.entry(3, "cloudy"),
        Map.entry(45, "fog"), Map.entry(48, "fog"),
        Map.entry(51, "lightrain"), Map.entry(53, "lightrain"), Map.entry(55, "rain"), Map.entry(56, "lightsleet"), Map.entry(57, "sleet"),
        Map.entry(61, "lightrain"), Map.entry(63, "rain"), Map.entry(65, "heavyrain"), Map.entry(66, "lightsleet"), Map.entry(67, "sleet"),
        Map.entry(71, "lightsnow"), Map.entry(73, "snow"), Map.entry(75, "heavysnow"), Map.entry(77, "snow"),
        Map.entry(80, "lightrainshowers_day"), Map.entry(81, "rainshowers_day"), Map.entry(82, "heavyrainshowers_day"),
        Map.entry(85, "lightsnowshowers_day"), Map.entry(86, "heavysnowshowers_day"),
        Map.entry(95, "rainandthunder"), Map.entry(96, "heavyrainandthunder"), Map.entry(99, "heavyrainandthunder"));

    static String symbol(@Nullable Integer wmoCode) {
        return wmoCode == null ? "cloudy" : SYMBOLS.getOrDefault(wmoCode, "cloudy");
    }

    static List<DayWeather> days(Response response) {

        var daily = response.daily();
        var result = new ArrayList<DayWeather>();
        for (int i = 0; i < daily.time().size(); i++) {
            var max = daily.temperature_2m_max().get(i);
            if (max == null) {
                continue;
            }
            var code = daily.weather_code() == null ? null : daily.weather_code().get(i);
            result.add(new DayWeather(LocalDate.parse(daily.time().get(i)), symbol(code), MetForecast.round(max),
                MetForecast.round(daily.temperature_2m_min().get(i)), MetForecast.round(orZero(daily.precipitation_sum().get(i))), "OPEN_METEO", false));
        }

        return result;
    }

    /** Average per calendar month over the archive: typical high/low, mm per day, share of days with rain. */
    static List<DayWeather.MonthClimate> climate(Response archive) {

        var daily = archive.daily();
        var maxSum = new double[12];
        var minSum = new double[12];
        var rainSum = new double[12];
        var rainDays = new int[12];
        var count = new int[12];
        for (int i = 0; i < daily.time().size(); i++) {
            var max = daily.temperature_2m_max().get(i);
            var min = daily.temperature_2m_min().get(i);
            if (max == null || min == null) {
                continue;
            }
            var month = LocalDate.parse(daily.time().get(i)).getMonthValue() - 1;
            var rain = orZero(daily.precipitation_sum().get(i));
            maxSum[month] += max;
            minSum[month] += min;
            rainSum[month] += rain;
            rainDays[month] += rain >= RAIN_DAY_MM ? 1 : 0;
            count[month]++;
        }

        var result = new ArrayList<DayWeather.MonthClimate>();
        for (int m = 0; m < 12; m++) {
            if (count[m] > 0) {
                result.add(new DayWeather.MonthClimate(m + 1, MetForecast.round(maxSum[m] / count[m]), MetForecast.round(minSum[m] / count[m]),
                    MetForecast.round(rainSum[m] / count[m]), Math.round(100f * rainDays[m] / count[m])));
            }
        }

        return result;
    }

    private static double orZero(@Nullable Double value) {
        return value == null ? 0 : value;
    }
}
