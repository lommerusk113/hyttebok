package no.hyttebok.chat;

import io.micronaut.context.annotation.Value;
import io.micronaut.data.jdbc.runtime.JdbcOperations;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;

/**
 * What the AI chat may cost per cabin and calendar month (default $1). The cost is counted from the token usage
 * OpenAI reports, at the model's list prices (per million tokens, configurable). Only the share of the cap is shown
 * in the app, never the amount.
 */
@Singleton
public class ChatBudget {

    private static final ZoneId ZONE = ZoneId.of("Europe/Oslo");
    private static final double MILLION = 1_000_000d;

    private final JdbcOperations jdbc;
    private final double capUsd;
    private final double inputPrice;
    private final double cachedPrice;
    private final double outputPrice;

    // Defaults: gpt-5-nano's prices. Set them when changing hyttebok.chat.model.
    public ChatBudget(JdbcOperations jdbc,
                      @Value("${hyttebok.chat.monthly-cap-usd:1.0}") double capUsd,
                      @Value("${hyttebok.chat.price.input:0.05}") double inputPrice,
                      @Value("${hyttebok.chat.price.cached-input:0.005}") double cachedPrice,
                      @Value("${hyttebok.chat.price.output:0.40}") double outputPrice) {
        this.jdbc = jdbc;
        this.capUsd = capUsd;
        this.inputPrice = inputPrice;
        this.cachedPrice = cachedPrice;
        this.outputPrice = outputPrice;
    }

    /** How much of this month's cap the cabin has used, 0–100 (rounded down; 100 = no more questions this month). */
    @Transactional(readOnly = true)
    public int percentUsed(long cabinId) {
        return percent(spent(cabinId));
    }

    public int percent(double spentUsd) {
        return (int) Math.min(100, Math.floor(spentUsd / capUsd * 100));
    }

    @Transactional(readOnly = true)
    public boolean exhausted(long cabinId) {
        return spent(cabinId) >= capUsd;
    }

    /** Adds one model call, from the {@code usage} object of OpenAI's response. */
    @Transactional
    public void record(long cabinId, Map<?, ?> usage) {

        var prompt = number(usage.get("prompt_tokens"));
        var output = number(usage.get("completion_tokens"));
        var cached = usage.get("prompt_tokens_details") instanceof Map<?, ?> details ? number(details.get("cached_tokens")) : 0;
        var cost = ((prompt - cached) * inputPrice + cached * cachedPrice + output * outputPrice) / MILLION;

        jdbc.prepareStatement("""
            INSERT INTO chat_usage (cabin_id, month, cost_usd) VALUES (?, ?, ?)
            ON CONFLICT (cabin_id, month) DO UPDATE SET cost_usd = chat_usage.cost_usd + excluded.cost_usd
            """, statement -> {
            statement.setLong(1, cabinId);
            statement.setObject(2, month());
            statement.setBigDecimal(3, BigDecimal.valueOf(cost));
            return statement.executeUpdate();
        });
    }

    private double spent(long cabinId) {
        return jdbc.prepareStatement("SELECT cost_usd FROM chat_usage WHERE cabin_id = ? AND month = ?", statement -> {

            statement.setLong(1, cabinId);
            statement.setObject(2, month());
            try (var rs = statement.executeQuery()) {
                return rs.next() ? rs.getDouble(1) : 0d;
            }
        });
    }

    /** The first day of this month: the row the month's usage is added to. */
    static LocalDate month() {
        return LocalDate.now(ZONE).withDayOfMonth(1);
    }

    private static long number(Object value) {
        return value instanceof Number n ? n.longValue() : 0;
    }
}
