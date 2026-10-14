package no.hyttebok.chat;

import io.micronaut.core.annotation.Nullable;

import java.util.List;
import java.util.Map;

/** The actions the chatbot may take, described for Gemini, and helpers to read its arguments. */
final class ChatTools {

    static final String ADD_SHOPPING = "add_shopping_item";
    static final String MARK_BOUGHT = "mark_shopping_item_bought";
    static final String ADD_TODO = "add_todo";
    static final String COMPLETE_TODO = "complete_todo";
    static final String CREATE_BOOKING = "create_booking";
    static final String COMPLETE_MAINTENANCE = "complete_maintenance";

    static final List<Map<String, Object>> DECLARATIONS = List.of(
        tool(ADD_SHOPPING, "Add one item to the shopping list.",
            Map.of("name", text("What to buy, e.g. 'Tørkepapir'"), "quantity", text("Optional amount, e.g. '2' or '5 l'")), List.of("name")),
        tool(MARK_BOUGHT, "Mark a shopping list item as bought.", Map.of("id", integer("The item's id")), List.of("id")),
        tool(ADD_TODO, "Add a one-off task to Vedlikehold (something that needs fixing or doing once, not recurring).",
            Map.of("title", text("Short title"), "description", text("Optional details")), List.of("title")),
        tool(COMPLETE_TODO, "Mark a one-off task as done.", Map.of("id", integer("The task's id")), List.of("id")),
        tool(CREATE_BOOKING, "Book the cabin for someone. Both dates are included (arrival day to departure day).",
            Map.of("guestName", text("Who is coming (whose booking it is)"), "startDate", text("Arrival day, YYYY-MM-DD"),
                "endDate", text("Departure day, YYYY-MM-DD"), "guests", integer("Optional number of people"), "note", text("Optional note")),
            List.of("guestName", "startDate", "endDate")),
        tool(COMPLETE_MAINTENANCE, "Record that a recurring maintenance task was done.",
            Map.of("id", integer("The task's id"), "date", text("Optional day it was done, YYYY-MM-DD; default today")), List.of("id")));

    private ChatTools() {
    }

    private static Map<String, Object> tool(String name, String description, Map<String, Object> properties, List<String> required) {
        return Map.of("name", name, "description", description,
            "parameters", Map.of("type", "OBJECT", "properties", properties, "required", required));
    }

    private static Map<String, Object> text(String description) {
        return Map.of("type", "STRING", "description", description);
    }

    private static Map<String, Object> integer(String description) {
        return Map.of("type", "INTEGER", "description", description);
    }

    static String text(Map<?, ?> args, String key) {

        var value = optionalText(args, key);
        if (value == null) {
            throw new IllegalArgumentException("Missing " + key);
        }

        return value;
    }

    @Nullable
    static String optionalText(Map<?, ?> args, String key) {
        return args.get(key) instanceof Object v && !String.valueOf(v).isBlank() ? String.valueOf(v).trim() : null;
    }

    @Nullable
    static Integer optionalInt(Map<?, ?> args, String key) {
        return args.get(key) instanceof Number n ? n.intValue() : null;
    }

    static Long id(Map<?, ?> args) {

        if (!(args.get("id") instanceof Number n)) {
            throw new IllegalArgumentException("Missing id");
        }

        return n.longValue();
    }
}
