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
import no.hyttebok.cabin.CabinContext;
import no.hyttebok.cabin.CabinInfoRepository;
import no.hyttebok.forum.ForumController;
import no.hyttebok.log.LogEntryController;
import no.hyttebok.maintenance.MaintenanceCompletion;
import no.hyttebok.maintenance.MaintenanceCompletionController;
import no.hyttebok.maintenance.MaintenanceController;
import no.hyttebok.push.MaintenanceReminderJob;
import no.hyttebok.shopping.ShoppingItem;
import no.hyttebok.shopping.ShoppingItemController;
import no.hyttebok.suggestion.SuggestionController;
import no.hyttebok.todo.Todo;
import no.hyttebok.todo.TodoController;
import no.hyttebok.weather.WeatherService;
import no.hyttebok.wiki.WikiAttachment;
import no.hyttebok.wiki.WikiAttachmentRepository;
import no.hyttebok.wiki.WikiPageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The cabin chatbot. Each question goes to OpenAI (gpt-5-nano by default) together with a summary of the cabin's data
 * (no WiFi password, no notes) and a few tools; tool calls run through the normal controllers, so
 * notifications and history behave exactly as when the change is made in the app. Nothing is deleted.
 */
@Singleton
public class ChatService {

    private static final Logger LOG = LoggerFactory.getLogger(ChatService.class);
    private static final ZoneId ZONE = ZoneId.of("Europe/Oslo");
    private static final int MAX_TOOL_ROUNDS = 5;
    private static final int MAX_HISTORY = 20;
    private static final int RECENT_BOOKINGS = 5;
    private static final int WEATHER_DAYS = 45;
    private static final int MAX_WIKI_CHARS = 60_000;
    private static final int FORUM_DAYS = 60;
    private static final int FORUM_POSTS = 20;
    private static final int LOGBOOK_ENTRIES = 10;
    private static final int SNIPPET = 400;
    // How to use the app: the same for every cabin, so it lives here rather than in each cabin's wiki.
    private static final String APP_GUIDE = resource("/chat/app-guide.txt");
    // The API is busy now and then (5xx): wait a little and try again.
    private static final List<Duration> RETRY_AFTER = List.of(Duration.ofSeconds(1), Duration.ofSeconds(3));

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
    private final WikiPageRepository wiki;
    private final WikiAttachmentRepository wikiFiles;
    private final ForumController forum;
    private final LogEntryController logbook;
    private final SuggestionController suggestions;
    private final JsonMapper json;
    private final ChatBudget budget;
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
                       @Value("${hyttebok.chat.model:gpt-5-nano}") String model,
                       @Value("${hyttebok.chat.url:`https://api.openai.com/v1/chat/completions`}") String apiUrl,
                       CabinInfoRepository cabins,
                       BookingController bookings,
                       ShoppingItemController shopping,
                       TodoController todos,
                       MaintenanceController maintenance,
                       MaintenanceCompletionController completions,
                       WeatherService weather,
                       WikiPageRepository wiki,
                       WikiAttachmentRepository wikiFiles,
                       ForumController forum,
                       LogEntryController logbook,
                       SuggestionController suggestions,
                       JsonMapper json,
                       ChatBudget budget) {
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
        this.wiki = wiki;
        this.wikiFiles = wikiFiles;
        this.forum = forum;
        this.logbook = logbook;
        this.suggestions = suggestions;
        this.json = json;
        this.budget = budget;
    }

    public Reply reply(Request request) {

        if (apiKey.isBlank()) {
            throw new HttpStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Chat is not set up (OPENAI_API_KEY)");
        }
        if (!cabins.findChatEnabled(CabinContext.require()).orElse(false)) {
            throw new HttpStatusException(HttpStatus.FORBIDDEN, "Chat is not turned on for this cabin");
        }
        if (budget.exhausted(CabinContext.require())) {
            throw new HttpStatusException(HttpStatus.PAYMENT_REQUIRED, "This month's chat budget is used up");
        }

        var english = "en".equals(request.lang());
        var user = request.name() == null || request.name().isBlank() ? null : request.name().trim();
        var messages = new ArrayList<Object>();
        messages.add(null); // the system message, rebuilt each round below
        var history = request.messages();
        for (var m : history.subList(Math.max(0, history.size() - MAX_HISTORY), history.size())) {
            messages.add(Map.of("role", "model".equals(m.role()) ? "assistant" : "user", "content", m.text()));
        }

        var actions = new ArrayList<String>();
        for (int round = 0; round < MAX_TOOL_ROUNDS; round++) {
            // The data is rebuilt each round, so a follow-up question sees what the tools just changed.
            messages.set(0, Map.of("role", "system", "content", instructions(user, english) + "\n\nAPP GUIDE (how Hyttebok works):\n" + APP_GUIDE
                + "\n\n" + context()));
            var message = generate(Map.of("model", model, "reasoning_effort", "low", "messages", messages, "tools", ChatTools.DECLARATIONS), user);
            if (message == null) {
                break;
            }
            messages.add(message); // as returned: the tool results below refer to its tool call ids

            var calls = message.get("tool_calls") instanceof List<?> list ? list : List.of();
            if (calls.isEmpty()) {
                return new Reply(message.get("content") instanceof String text ? text.trim() : "", actions);
            }

            for (var call : calls) {
                var toolCall = (Map<?, ?>) call;
                var function = (Map<?, ?>) toolCall.get("function");
                var name = String.valueOf(function.get("name"));
                Map<String, Object> result;
                try {
                    result = run(name, arguments(function.get("arguments")), user, english, actions);
                } catch (RuntimeException e) {
                    result = Map.of("error", String.valueOf(e.getMessage()));
                }
                messages.add(Map.of("role", "tool", "tool_call_id", String.valueOf(toolCall.get("id")), "content", toJson(result)));
            }
        }

        return new Reply(english ? "Sorry, I couldn't finish that. Try asking again." : "Beklager, det fikk jeg ikke til. Prøv å spørre på nytt.", actions);
    }

    private String instructions(@Nullable String user, boolean english) {
        return """
            You are the assistant in Hyttebok, the family cabin app. Answer in %s, short and friendly, as plain text \
            (no markdown; start a line with "- " for a list).
            The person asking is %s. Today is %s.
            Use only the data below; if something isn't there, say you don't know. For how things work at the cabin \
            (water, power, heating, keys, where things are, routines), look in the CABIN WIKI first: answer from the one page that fits best, and don't add steps, fallbacks or tips that \
            aren't written there, or mix in other pages unless that page refers to them. For how to use the \
            app itself (booking, inviting, installing, notifications), use the APP GUIDE (it is in Norwegian; translate if needed). Use the ids from the data, never invent them. The ids are only for the tools: never show or mention them to the person.
            You can change things with the tools. For a booking, make sure you know who, the first day and the last day; \
            if anything is unclear (e.g. which weekend), ask before booking. If dates overlap an existing booking, say so.
            After using a tool, say briefly what you did. Talk about dates naturally (e.g. "fredag 16. oktober"), not as ISO.
            """.formatted(english ? "English" : "Norwegian (bokmål)", user == null ? "unknown (ask for their name before booking for them)" : user,
            LocalDate.now(ZONE) + " (" + LocalDate.now(ZONE).getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + ")");
    }

    /** The cabin's data as compact text, with the wiki. Left out on purpose: WiFi password and notes. */
    private String context() {

        var today = LocalDate.now(ZONE);
        var out = new StringBuilder();

        var cabin = cabins.findById(CabinContext.require()).orElseThrow();
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
        // Codes stay out of the AI request, like the WiFi password.
        if (!hidden.contains("keyInfo") && cabin.keyInfo() != null && !cabin.keyInfo().isBlank()) {
            out.append("\nKeys (who has one, the spare, the key box code): see the Keys card on the overview in the app");
        }

        // ponytail: every page goes in, up to a cap; search the wiki with a tool instead if it outgrows this.
        var pages = wiki.listOrderByTitle();
        // The chat can't read the files, but can point to them ("the manual is attached to the Varmepumpe page").
        var files = wikiFiles.listOrderByCreatedAt().stream()
            .collect(Collectors.groupingBy(WikiAttachment::wikiPageId, Collectors.mapping(WikiAttachment::originalName,
                Collectors.joining(", "))));
        if (!pages.isEmpty()) {
            out.append("\n\nCABIN WIKI (written by the family):");
            var room = MAX_WIKI_CHARS;
            for (var page : pages) {
                var text = "\n## " + page.title() + "\n" + (page.content() == null ? "" : page.content().trim())
                    + (files.containsKey(page.id()) ? "\n(Files attached to this page in the app: " + files.get(page.id()) + ")" : "");
                if (text.length() > room) {
                    out.append("\n(more wiki pages left out: too long)");
                    break;
                }
                out.append(text);
                room -= text.length();
            }
        }

        var all = bookings.list();
        out.append("\n\nBOOKINGS (upcoming and current):");
        all.stream().filter(b -> !b.endDate().isBefore(today)).forEach(b -> out.append('\n').append(booking(b)));
        out.append("\nRecent past bookings:");
        all.stream().filter(b -> b.endDate().isBefore(today)).sorted((a, b) -> b.endDate().compareTo(a.endDate())).limit(RECENT_BOOKINGS)
            .forEach(b -> out.append('\n').append(booking(b)));

        out.append("\n\nSHOPPING LIST (to buy):");
        shopping.list().stream().filter(i -> !i.bought()).forEach(i -> out.append("\n- ").append(i.name())
            .append(i.quantity() == null ? "" : " (" + i.quantity() + ")").append(i.addedBy() == null ? "" : ", added by " + i.addedBy()).append(id(i.id())));

        out.append("\n\nONE-OFF TASKS (open; shown with the recurring ones under Vedlikehold):");
        todos.list().stream().filter(x -> !x.done()).forEach(x -> out.append("\n- ").append(x.title())
            .append(x.description() == null ? "" : " – " + x.description()).append(x.createdBy() == null ? "" : ", added by " + x.createdBy())
            .append(x.claimedBy() == null ? "" : ", " + x.claimedBy() + " will do it").append(id(x.id())));

        out.append("\n\nRECURRING MAINTENANCE (monthly/yearly tasks are due within their month):");
        for (var m : maintenance.list()) {
            var dueBy = MaintenanceReminderJob.dueBy(m);
            out.append("\n- ").append(m.name()).append(", every ").append(m.intervalValue()).append(' ')
                .append(m.intervalUnit().toLowerCase(Locale.ROOT)).append(", last done ").append(m.lastCompleted() == null ? "never" : m.lastCompleted())
                .append(", next due ").append(MaintenanceReminderJob.byMonth(m) ? MaintenanceReminderJob.nextDue(m).toString().substring(0, 7) : MaintenanceReminderJob.nextDue(m))
                .append(dueBy.isBefore(today) ? " (OVERDUE)" : "").append(id(m.id()));
        }

        // What was done this year, with who took part and what they wrote (the "Utført i år" list).
        out.append("\n\nDONE THIS YEAR (newest first):");
        var names = maintenance.list().stream().collect(Collectors.toMap(m -> m.id(), m -> m.name()));
        var done = new ArrayList<Map.Entry<LocalDate, String>>();
        for (var c : completions.list()) {
            if (c.completedOn().getYear() == today.getYear() && names.containsKey(c.maintenanceId())) {
                done.add(Map.entry(c.completedOn(), names.get(c.maintenanceId()) + " (recurring)" + who(c.participants(), c.completedBy()) + note(c.note())));
            }
        }
        for (var x : todos.list()) {
            if (x.done() && x.doneAt() != null && x.doneAt().atZone(ZONE).getYear() == today.getYear()) {
                done.add(Map.entry(x.doneAt().atZone(ZONE).toLocalDate(), x.title() + " (one-off)" + who(x.doneWith(), x.doneBy()) + note(x.doneNote())));
            }
        }
        done.sort(Map.Entry.<LocalDate, String>comparingByKey().reversed());
        done.forEach(d -> out.append("\n- ").append(d.getKey()).append(": ").append(d.getValue()));

        out.append("\n\nOPEN SUGGESTIONS (ideas; DO = something to do, BUY = something to buy):");
        suggestions.list().stream().filter(s -> !s.done()).forEach(s -> out.append("\n- ").append(s.title()).append(" (").append(s.category()).append(", ")
            .append(s.voters().size()).append(" votes").append(s.author() == null ? "" : ", by " + s.author()).append(")").append(text(": ", s.description())));

        out.append("\n\nCABIN BOOK (the latest stories from stays, newest first):");
        logbook.list().stream().limit(LOGBOOK_ENTRIES).forEach(e -> out.append("\n- ").append(e.title())
            .append(e.startDate() == null ? "" : " (" + e.startDate() + (e.endDate() == null || e.endDate().equals(e.startDate()) ? "" : " to " + e.endDate()) + ")")
            .append(", by ").append(e.author()).append(e.participants().isEmpty() ? "" : ", with " + String.join(", ", e.participants()))
            .append(text(": ", e.content())));

        out.append("\n\nFORUM (posts with activity in the last " + FORUM_DAYS + " days, latest activity first):");
        var since = Instant.now().minus(Duration.ofDays(FORUM_DAYS));
        forum.list(null, "active").stream().filter(p -> !p.lastActivity().isBefore(since)).limit(FORUM_POSTS).forEach(p -> {
            out.append("\n- ").append(p.createdAt().atZone(ZONE).toLocalDate()).append(" ").append(p.author()).append(": ")
                .append(p.title() == null ? "" : p.title() + " – ").append(shorten(p.body())).append(reactions(p.reactions().size()));
            p.comments().forEach(c -> out.append("\n  - comment ").append(c.createdAt().atZone(ZONE).toLocalDate()).append(" ").append(c.author())
                .append(": ").append(shorten(c.body())).append(reactions(c.reactions().size())));
        });

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

    private static String who(@Nullable List<String> participants, @Nullable String by) {
        return participants != null && !participants.isEmpty() ? ", by " + String.join(", ", participants) : by == null ? "" : ", by " + by;
    }

    private static String note(@Nullable String note) {
        return text(" – note: ", note);
    }

    private static String text(String label, @Nullable String value) {
        return value == null || value.isBlank() ? "" : label + shorten(value);
    }

    private static String shorten(@Nullable String text) {

        var flat = text == null ? "" : text.replace('\n', ' ').trim();

        return flat.length() <= SNIPPET ? flat : flat.substring(0, SNIPPET) + "…";
    }

    private static String reactions(int count) {
        return count == 0 ? "" : " (" + count + " reactions)";
    }

    /** At the end of a line, so lists copied into an answer don't start with it (the person never sees ids). */
    private static String id(Long id) {
        return " [id " + id + "]";
    }

    private static String booking(Booking b) {
        return "- " + b.guestName() + ", " + b.startDate() + " to " + b.endDate()
            + (b.guests() == null ? "" : ", " + b.guests() + " people") + (b.note() == null || b.note().isBlank() ? "" : ", note: " + b.note())
            + (b.checkedInAt() == null ? "" : b.checkedOutAt() == null ? ", checked in (at the cabin now)" : ", checked out") + id(b.id());
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
                var saved = shopping.create(new ShoppingItem(null, name, ChatTools.optionalText(args, "quantity"), false, null, null, user, null, null));
                actions.add(english ? "Added “" + name + "” to the shopping list" : "La til «" + name + "» på handlelista");
                return Map.of("ok", true, "id", saved.id());
            }
            case ChatTools.MARK_BOUGHT -> {
                var id = ChatTools.id(args);
                var item = shopping.list().stream().filter(i -> i.id().equals(id)).findFirst().orElseThrow(() -> new IllegalArgumentException("No shopping item " + id));
                shopping.update(new ShoppingItem(item.id(), item.name(), item.quantity(), true, user, Instant.now(), item.addedBy(), item.createdAt(), item.cabinId()));
                actions.add(english ? "Marked “" + item.name() + "” as bought" : "Krysset av «" + item.name() + "» som kjøpt");
                return Map.of("ok", true);
            }
            case ChatTools.ADD_TODO -> {
                var title = ChatTools.text(args, "title");
                var saved = todos.create(new Todo(null, title, ChatTools.optionalText(args, "description"), false, null, null, user, null, null, null, null, null));
                actions.add(english ? "Added the to-do “" + title + "”" : "La til gjøremålet «" + title + "»");
                return Map.of("ok", true, "id", saved.id());
            }
            case ChatTools.COMPLETE_TODO -> {
                var id = ChatTools.id(args);
                var todo = todos.list().stream().filter(x -> x.id().equals(id)).findFirst().orElseThrow(() -> new IllegalArgumentException("No to-do " + id));
                todos.update(new Todo(todo.id(), todo.title(), todo.description(), true, user, Instant.now(), todo.createdBy(), todo.createdAt(), todo.doneWith(), todo.doneNote(), todo.claimedBy(), todo.cabinId()));
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
                var saved = bookings.create(new Booking(null, guest, start, end, ChatTools.optionalInt(args, "guests"), ChatTools.optionalText(args, "note"), null, null, null, null));
                actions.add(english ? "Booked " + start + " – " + end + " for " + guest : "Booket " + start + " – " + end + " for " + guest);
                return Map.of("ok", true, "id", saved.id(), "overlapsWith", overlaps);
            }
            case ChatTools.COMPLETE_MAINTENANCE -> {
                var id = ChatTools.id(args);
                var task = maintenance.list().stream().filter(m -> m.id().equals(id)).findFirst().orElseThrow(() -> new IllegalArgumentException("No maintenance task " + id));
                var date = ChatTools.optionalText(args, "date");
                completions.create(new MaintenanceCompletion(null, id, date == null ? today : LocalDate.parse(date), user, null, null, null, null));
                actions.add(english ? "Marked “" + task.name() + "” as done" : "Markerte «" + task.name() + "» som utført");
                return Map.of("ok", true);
            }
            default -> throw new IllegalArgumentException("Unknown tool " + tool);
        }
    }

    /** One call to the model, tried again (after a pause) while the API is busy or unreachable. */
    private HttpResponse<byte[]> send(Map<String, Object> body) {

        for (var attempt = 0; ; attempt++) {
            var last = attempt == RETRY_AFTER.size();
            try {
                var request = HttpRequest.newBuilder(URI.create(apiUrl))
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(json.writeValueAsBytes(body)))
                    .build();
                var response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
                if (last || response.statusCode() < 500) {
                    return response;
                }
                LOG.info("OpenAI {}, trying again", response.statusCode());
            } catch (IOException e) {
                if (last) {
                    throw new HttpStatusException(HttpStatus.BAD_GATEWAY, "OpenAI unreachable");
                }
                LOG.info("OpenAI unreachable, trying again: {}", e.toString());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new HttpStatusException(HttpStatus.BAD_GATEWAY, "Interrupted");
            }
            try {
                Thread.sleep(RETRY_AFTER.get(attempt));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new HttpStatusException(HttpStatus.BAD_GATEWAY, "Interrupted");
            }
        }
    }

    /** The reply message ({@code content} and/or {@code tool_calls}), or null if there was none. */
    @Nullable
    private Map<?, ?> generate(Map<String, Object> body, @Nullable String person) {

        var response = send(body);

        if (response.statusCode() == 429) {
            LOG.warn("OpenAI 429: {}", new String(response.body()));
            throw new HttpStatusException(HttpStatus.TOO_MANY_REQUESTS, "OpenAI rate limit or quota");
        }
        if (response.statusCode() != 200) {
            LOG.warn("OpenAI {}: {}", response.statusCode(), new String(response.body()));
            throw new HttpStatusException(HttpStatus.BAD_GATEWAY, "OpenAI error " + response.statusCode());
        }

        try {
            Map<?, ?> result = json.readValue(response.body(), Argument.mapOf(String.class, Object.class));
            if (result.get("usage") instanceof Map<?, ?> usage) {
                budget.record(CabinContext.require(), usage, person == null ? "?" : person);
            }

            var choices = result.get("choices") instanceof List<?> list ? list : List.of();
            if (choices.isEmpty() || !(choices.get(0) instanceof Map<?, ?> first)) {
                return null;
            }

            return first.get("message") instanceof Map<?, ?> message ? message : null;
        } catch (IOException e) {
            throw new HttpStatusException(HttpStatus.BAD_GATEWAY, "Unreadable OpenAI response");
        }
    }

    /** Tool arguments come as a JSON string. */
    private Map<?, ?> arguments(@Nullable Object raw) {
        try {
            return raw instanceof String text && !text.isBlank() ? json.readValue(text, Argument.mapOf(String.class, Object.class)) : Map.of();
        } catch (IOException e) {
            throw new IllegalArgumentException("Unreadable arguments");
        }
    }

    private String toJson(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String resource(String path) {

        var in = ChatService.class.getResourceAsStream(path);
        if (in == null) {
            throw new IllegalStateException("Missing " + path);
        }
        try (in) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
