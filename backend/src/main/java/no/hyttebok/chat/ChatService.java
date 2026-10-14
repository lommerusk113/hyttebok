package no.hyttebok.chat;

import io.micronaut.context.annotation.Value;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.json.JsonMapper;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.inject.Singleton;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import no.hyttebok.booking.Booking;
import no.hyttebok.booking.BookingController;
import no.hyttebok.cabin.CabinInfoRepository;
import no.hyttebok.maintenance.MaintenanceCompletion;
import no.hyttebok.maintenance.MaintenanceCompletionController;
import no.hyttebok.maintenance.MaintenanceController;
import no.hyttebok.push.MaintenanceReminderJob;
import no.hyttebok.shopping.ShoppingItem;
import no.hyttebok.shopping.ShoppingItemController;
import no.hyttebok.todo.Todo;
import no.hyttebok.todo.TodoController;
import no.hyttebok.weather.WeatherService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The cabin chatbot. Each question goes to Google Gemini together with a summary of the cabin's data
 * (no WiFi password, no notes) and a few tools; tool calls run through the normal controllers, so
 * notifications and history behave exactly as when the change is made in the app. Nothing is deleted.
 */
@Singleton
public class ChatService {

    private static final Logger LOG = LoggerFactory.getLogger(ChatService.class);
    private static final ZoneId ZONE = ZoneId.of("Europe/Oslo");
    private static final long CABIN_ID = 1L;
    private static final int MAX_TOOL_ROUNDS = 5;
    private static final int MAX_HISTORY = 20;
    private static final int RECENT_BOOKINGS = 5;
    private static final int WEATHER_DAYS = 45;

    private final String apiKey;
    private final String model;
    private final String apiUrl;
    private final CabinInfoRepository cabins;
    private final BookingController bookings;
    private final ShoppingItemController shopping;
    private final TodoController todos;
    private final MaintenanceController maintenance;
    private final MaintenanceCompletionController completions;
    private final WeatherService weather;
    private final JsonMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    @Serdeable
    public record Message(@NotBlank @Pattern(regexp = "user|model") String role, @NotBlank @Size(max = 4000) String text) {
    }

    @Serdeable
    public record Request(@NotEmpty @Size(max = 60) List<@Valid Message> messages, @Nullable String name, @Nullable String lang) {
    }

    /** {@code actions}: what the bot changed, in words, so the app can show it and refresh. */
    @Serdeable
    public record Reply(String text, List<String> actions) {
    }

    public ChatService(@Value("${hyttebok.chat.api-key:}") String apiKey,
                       @Value("${hyttebok.chat.model:gemini-flash-latest}") String model,
                       @Value("${hyttebok.chat.url:`https://generativelanguage.googleapis.com/v1beta/models/`}") String apiUrl,
                       CabinInfoRepository cabins,
                       BookingController bookings,
                       ShoppingItemController shopping,
                       TodoController todos,
                       MaintenanceController maintenance,
                       MaintenanceCompletionController completions,
                       WeatherService weather,
                       JsonMapper json) {
        this.apiKey = apiKey;
        this.model = model;
        this.apiUrl = apiUrl;
        this.cabins = cabins;
        this.bookings = bookings;
        this.shopping = shopping;
        this.todos = todos;
        this.maintenance = maintenance;
        this.completions = completions;
        this.weather = weather;
        this.json = json;
    }

    public Reply reply(Request request) {

        if (apiKey.isBlank()) {
            throw new HttpStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Chat is not set up (GEMINI_API_KEY)");
        }

        var english = "en".equals(request.lang());
        var user = request.name() == null || request.name().isBlank() ? null : request.name().trim();
        var contents = new ArrayList<Object>();
        var history = request.messages();
        for (var m : history.subList(Math.max(0, history.size() - MAX_HISTORY), history.size())) {
            contents.add(Map.of("role", m.role(), "parts", List.of(Map.of("text", m.text()))));
        }

        var actions = new ArrayList<String>();
        for (int round = 0; round < MAX_TOOL_ROUNDS; round++) {
            // The data is rebuilt each round, so a follow-up question sees what the tools just changed.
            var body = Map.of(
                "systemInstruction", Map.of("parts", List.of(Map.of("text", instructions(user, english) + "\n\n" + context()))),
                "contents", contents,
                "tools", List.of(Map.of("functionDeclarations", ChatTools.DECLARATIONS)));
            var content = generate(body);
            if (content == null) {
                break;
            }
            contents.add(content); // as returned, so Gemini's own bookkeeping (e.g. thought signatures) survives

            var parts = parts(content);
            var calls = parts.stream().filter(p -> p.get("functionCall") instanceof Map).<Map<?, ?>>map(p -> (Map<?, ?>) p.get("functionCall")).toList();
            if (calls.isEmpty()) {
                var text = new StringBuilder();
                parts.stream().filter(p -> p.get("text") instanceof String && !Boolean.TRUE.equals(p.get("thought")))
                    .forEach(p -> text.append(p.get("text")));
                return new Reply(text.toString().trim(), actions);
            }

            var results = new ArrayList<Object>();
            for (var call : calls) {
                var name = String.valueOf(call.get("name"));
                var args = call.get("args") instanceof Map<?, ?> a ? a : Map.of();
                Map<String, Object> result;
                try {
                    result = run(name, args, user, english, actions);
                } catch (RuntimeException e) {
                    result = Map.of("error", String.valueOf(e.getMessage()));
                }
                results.add(Map.of("functionResponse", Map.of("name", name, "response", result)));
            }
            contents.add(Map.of("role", "user", "parts", results));
        }

        return new Reply(english ? "Sorry, I couldn't finish that. Try asking again." : "Beklager, det fikk jeg ikke til. Prøv å spørre på nytt.", actions);
    }

    private String instructions(@Nullable String user, boolean english) {
        return """
            You are the assistant in Hyttebok, the family cabin app. Answer in %s, short and friendly, as plain text \
            (no markdown; start a line with "- " for a list).
            The person asking is %s. Today is %s.
            Use only the data below; if something isn't there, say you don't know. Use the ids from the data, never invent them.
            You can change things with the tools. For a booking, make sure you know who, the first day and the last day; \
            if anything is unclear (e.g. which weekend), ask before booking. If dates overlap an existing booking, say so.
            After using a tool, say briefly what you did. Talk about dates naturally (e.g. "fredag 16. oktober"), not as ISO.
            """.formatted(english ? "English" : "Norwegian (bokmål)", user == null ? "unknown (ask for their name before booking for them)" : user,
            LocalDate.now(ZONE) + " (" + LocalDate.now(ZONE).getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + ")");
    }

    /** The cabin's data as compact text. Left out on purpose: WiFi password and notes. */
    private String context() {

        var today = LocalDate.now(ZONE);
        var out = new StringBuilder();

        var cabin = cabins.findById(CABIN_ID).orElseThrow();
        var hidden = cabin.hiddenSections();
        out.append("CABIN: ").append(cabin.name());
        append(out, " – ", cabin.tagline());
        append(out, "\nAbout: ", cabin.description());
        if (!hidden.contains("address")) {
            append(out, "\nAddress: ", cabin.address());
        }
        if (!hidden.contains("arrivalInfo")) {
            append(out, "\nOn arrival:\n", cabin.arrivalInfo());
        }
        if (!hidden.contains("departureInfo")) {
            append(out, "\nOn departure:\n", cabin.departureInfo());
        }
        if (!hidden.contains("wifi") && cabin.wifiName() != null) {
            out.append("\nWiFi network: ").append(cabin.wifiName()).append(" (password: see the WiFi card in the app)");
        }

        var all = bookings.list();
        out.append("\n\nBOOKINGS (upcoming and current):");
        all.stream().filter(b -> !b.endDate().isBefore(today)).forEach(b -> out.append('\n').append(booking(b)));
        out.append("\nRecent past bookings:");
        all.stream().filter(b -> b.endDate().isBefore(today)).sorted((a, b) -> b.endDate().compareTo(a.endDate())).limit(RECENT_BOOKINGS)
            .forEach(b -> out.append('\n').append(booking(b)));

        out.append("\n\nSHOPPING LIST (to buy):");
        shopping.list().stream().filter(i -> !i.bought()).forEach(i -> out.append("\n- id ").append(i.id()).append(": ").append(i.name())
            .append(i.quantity() == null ? "" : " (" + i.quantity() + ")").append(i.addedBy() == null ? "" : ", added by " + i.addedBy()));

        out.append("\n\nTO-DOS (open):");
        todos.list().stream().filter(x -> !x.done()).forEach(x -> out.append("\n- id ").append(x.id()).append(": ").append(x.title())
            .append(x.description() == null ? "" : " – " + x.description()).append(x.createdBy() == null ? "" : ", added by " + x.createdBy()));

        out.append("\n\nMAINTENANCE (recurring; monthly/yearly tasks are due within their month):");
        for (var m : maintenance.list()) {
            var dueBy = MaintenanceReminderJob.dueBy(m);
            out.append("\n- id ").append(m.id()).append(": ").append(m.name()).append(", every ").append(m.intervalValue()).append(' ')
                .append(m.intervalUnit().toLowerCase(Locale.ROOT)).append(", last done ").append(m.lastCompleted() == null ? "never" : m.lastCompleted())
                .append(", next due ").append(MaintenanceReminderJob.byMonth(m) ? MaintenanceReminderJob.nextDue(m).toString().substring(0, 7) : MaintenanceReminderJob.nextDue(m))
                .append(dueBy.isBefore(today) ? " (OVERDUE)" : "");
        }

        out.append("\n\nWEATHER at the cabin (date: max/min °C, mm; source FORECAST, or NORMAL = usual for the date, no forecast yet):");
        try {
            for (var d : weather.days(today, today.plusDays(WEATHER_DAYS))) {
                out.append('\n').append(d.date()).append(": ").append(Math.round(d.maxTemp())).append('/').append(Math.round(d.minTemp()))
                    .append(", ").append(d.precipitation()).append(" mm").append(d.symbol() == null ? ", NORMAL" : ", " + d.symbol());
            }
        } catch (RuntimeException e) {
            out.append("\n(unavailable)");
        }

        return out.toString();
    }

    private static String booking(Booking b) {
        return "- id " + b.id() + ": " + b.guestName() + ", " + b.startDate() + " to " + b.endDate()
            + (b.guests() == null ? "" : ", " + b.guests() + " people") + (b.note() == null || b.note().isBlank() ? "" : ", note: " + b.note());
    }

    private static void append(StringBuilder out, String label, @Nullable String value) {
        if (value != null && !value.isBlank()) {
            out.append(label).append(value.trim());
        }
    }

    private Map<String, Object> run(String tool, Map<?, ?> args, @Nullable String user, boolean english, List<String> actions) {

        var today = LocalDate.now(ZONE);
        switch (tool) {
            case ChatTools.ADD_SHOPPING -> {
                var name = ChatTools.text(args, "name");
                var saved = shopping.create(new ShoppingItem(null, name, ChatTools.optionalText(args, "quantity"), false, null, null, user, null));
                actions.add(english ? "Added “" + name + "” to the shopping list" : "La til «" + name + "» på handlelista");
                return Map.of("ok", true, "id", saved.id());
            }
            case ChatTools.MARK_BOUGHT -> {
                var id = ChatTools.id(args);
                var item = shopping.list().stream().filter(i -> i.id().equals(id)).findFirst().orElseThrow(() -> new IllegalArgumentException("No shopping item " + id));
                shopping.update(new ShoppingItem(item.id(), item.name(), item.quantity(), true, user, Instant.now(), item.addedBy(), item.createdAt()));
                actions.add(english ? "Marked “" + item.name() + "” as bought" : "Krysset av «" + item.name() + "» som kjøpt");
                return Map.of("ok", true);
            }
            case ChatTools.ADD_TODO -> {
                var title = ChatTools.text(args, "title");
                var saved = todos.create(new Todo(null, title, ChatTools.optionalText(args, "description"), false, null, null, user, null));
                actions.add(english ? "Added the to-do “" + title + "”" : "La til gjøremålet «" + title + "»");
                return Map.of("ok", true, "id", saved.id());
            }
            case ChatTools.COMPLETE_TODO -> {
                var id = ChatTools.id(args);
                var todo = todos.list().stream().filter(x -> x.id().equals(id)).findFirst().orElseThrow(() -> new IllegalArgumentException("No to-do " + id));
                todos.update(new Todo(todo.id(), todo.title(), todo.description(), true, user, Instant.now(), todo.createdBy(), todo.createdAt()));
                actions.add(english ? "Marked “" + todo.title() + "” as done" : "Markerte «" + todo.title() + "» som utført");
                return Map.of("ok", true);
            }
            case ChatTools.CREATE_BOOKING -> {
                var start = LocalDate.parse(ChatTools.text(args, "startDate"));
                var end = LocalDate.parse(ChatTools.text(args, "endDate"));
                if (end.isBefore(start)) {
                    throw new IllegalArgumentException("endDate is before startDate");
                }
                var guest = ChatTools.text(args, "guestName");
                var overlaps = bookings.list().stream().filter(b -> !b.startDate().isAfter(end) && !b.endDate().isBefore(start)).map(ChatService::booking).toList();
                var saved = bookings.create(new Booking(null, guest, start, end, ChatTools.optionalInt(args, "guests"), ChatTools.optionalText(args, "note")));
                actions.add(english ? "Booked " + start + " – " + end + " for " + guest : "Booket " + start + " – " + end + " for " + guest);
                return Map.of("ok", true, "id", saved.id(), "overlapsWith", overlaps);
            }
            case ChatTools.COMPLETE_MAINTENANCE -> {
                var id = ChatTools.id(args);
                var task = maintenance.list().stream().filter(m -> m.id().equals(id)).findFirst().orElseThrow(() -> new IllegalArgumentException("No maintenance task " + id));
                var date = ChatTools.optionalText(args, "date");
                completions.create(new MaintenanceCompletion(null, id, date == null ? today : LocalDate.parse(date), user, null));
                actions.add(english ? "Marked “" + task.name() + "” as done" : "Markerte «" + task.name() + "» som utført");
                return Map.of("ok", true);
            }
            default -> throw new IllegalArgumentException("Unknown tool " + tool);
        }
    }

    @Nullable
    private Map<?, ?> generate(Map<String, Object> body) {

        HttpResponse<byte[]> response;
        try {
            var request = HttpRequest.newBuilder(URI.create(apiUrl + model + ":generateContent"))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", apiKey)
                .POST(HttpRequest.BodyPublishers.ofByteArray(json.writeValueAsBytes(body)))
                .build();
            response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (IOException e) {
            throw new HttpStatusException(HttpStatus.BAD_GATEWAY, "Gemini unreachable");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new HttpStatusException(HttpStatus.BAD_GATEWAY, "Interrupted");
        }

        if (response.statusCode() == 429) {
            throw new HttpStatusException(HttpStatus.TOO_MANY_REQUESTS, "Gemini quota used up");
        }
        if (response.statusCode() != 200) {
            LOG.warn("Gemini {}: {}", response.statusCode(), new String(response.body()));
            throw new HttpStatusException(HttpStatus.BAD_GATEWAY, "Gemini error " + response.statusCode());
        }

        try {
            Map<?, ?> result = json.readValue(response.body(), Argument.mapOf(String.class, Object.class));
            var candidates = result.get("candidates") instanceof List<?> list ? list : List.of();
            if (candidates.isEmpty() || !(candidates.get(0) instanceof Map<?, ?> first)) {
                return null;
            }

            return first.get("content") instanceof Map<?, ?> content ? content : null;
        } catch (IOException e) {
            throw new HttpStatusException(HttpStatus.BAD_GATEWAY, "Unreadable Gemini response");
        }
    }

    private static List<Map<?, ?>> parts(Map<?, ?> content) {
        var parts = content.get("parts") instanceof List<?> list ? list : List.of();
        return parts.stream().filter(p -> p instanceof Map<?, ?>).<Map<?, ?>>map(p -> (Map<?, ?>) p).toList();
    }
}
