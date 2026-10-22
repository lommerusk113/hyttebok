package no.hyttebok.search;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.jdbc.runtime.JdbcOperations;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.transaction.annotation.Transactional;
import no.hyttebok.cabin.CabinContext;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Search across the cabin of the request: bookings, the cabin book, forum (posts and replies), wiki (pages and
 * their files), tasks, maintenance, ideas, the shopping list and expenses. Case-insensitive substring match
 * (position(), so "%" and "_" are plain characters); newest first, up to {@value #PER_KIND} hits of each kind.
 */
@Controller("/api/search")
public class SearchController {

    private static final int MIN_LENGTH = 2;
    private static final int PER_KIND = 10;
    private static final int AROUND = 60;

    /**
     * One kind of content: {@code title} and {@code text} are SQL expressions (what's shown and searched), {@code ref}
     * what the app needs for the link (e.g. the page of a wiki file). {@code from} may join; its table is "t".
     */
    private record Kind(String type, String from, String title, String text, String date, String ref) {
    }

    private static final List<Kind> KINDS = List.of(
        new Kind("booking", "booking t", "t.guest_name", "t.note", "t.start_date::timestamptz", "NULL"),
        new Kind("log", "log_entry t", "coalesce(t.title, t.author)", "t.content", "t.created_at", "NULL"),
        new Kind("forum", "forum_post t", "coalesce(t.title, t.author)", "t.body", "t.created_at", "NULL"),
        new Kind("reply", "forum_reply t JOIN forum_post p ON p.id = t.post_id", "coalesce(p.title, p.author)", "t.body", "t.created_at", "t.post_id::text"),
        new Kind("wiki", "wiki_page t", "t.title", "t.content", "t.updated_at", "NULL"),
        new Kind("file", "wiki_attachment t JOIN wiki_page p ON p.id = t.wiki_page_id", "t.original_name", "p.title", "t.created_at", "t.wiki_page_id::text"),
        new Kind("todo", "todo t", "t.title", "t.description", "t.created_at", "NULL"),
        new Kind("maintenance", "maintenance t", "t.name", "t.description", "t.created_at", "NULL"),
        new Kind("idea", "suggestion t", "t.title", "t.description", "t.created_at", "t.category"),
        new Kind("shopping", "shopping_item t", "t.name", "t.quantity", "t.created_at", "NULL"),
        new Kind("expense", "expense t", "coalesce(t.description, t.paid_by)", "array_to_string(t.split_between, ', ')", "t.spent_on::timestamptz", "NULL"));

    private final JdbcOperations jdbc;

    public SearchController(JdbcOperations jdbc) {
        this.jdbc = jdbc;
    }

    /** {@code type}: which kind (booking, log, forum, reply, wiki, file, todo, maintenance, idea, shopping, expense). */
    @Serdeable
    public record Hit(String type, Long id, String title, @Nullable String snippet, @Nullable Instant date, @Nullable String ref) {
    }

    @Get
    @Transactional(readOnly = true)
    public List<Hit> search(@QueryValue(defaultValue = "") String q) {

        var query = q.trim().toLowerCase(Locale.ROOT);
        if (query.length() < MIN_LENGTH) {
            return List.of();
        }
        var cabin = CabinContext.require();

        var hits = new ArrayList<Hit>();
        for (var kind : KINDS) {
            hits.addAll(find(kind, query, cabin));
        }
        hits.sort(Comparator.comparing(Hit::date, Comparator.nullsLast(Comparator.reverseOrder())));

        return hits;
    }

    private List<Hit> find(Kind kind, String query, long cabin) {

        var sql = "SELECT t.id, " + kind.title() + " AS title, " + kind.text() + " AS text, " + kind.date() + " AS date, " + kind.ref() + " AS ref"
            + " FROM " + kind.from() + " WHERE t.cabin_id = ? AND (position(? IN lower(coalesce(" + kind.title() + ", ''))) > 0"
            + " OR position(? IN lower(coalesce(" + kind.text() + ", ''))) > 0) ORDER BY 4 DESC NULLS LAST LIMIT " + PER_KIND;

        return jdbc.prepareStatement(sql, statement -> {

            statement.setLong(1, cabin);
            statement.setString(2, query);
            statement.setString(3, query);
            var found = new ArrayList<Hit>();
            try (var rs = statement.executeQuery()) {
                while (rs.next()) {
                    var date = rs.getTimestamp("date");
                    found.add(new Hit(kind.type(), rs.getLong("id"), rs.getString("title"), snippet(rs.getString("text"), query),
                        date == null ? null : date.toInstant(), rs.getString("ref")));
                }
            }

            return found;
        });
    }

    /** The text around the first match ("… slå på hovedbryteren i gangen …"), or its start when the title matched. */
    @Nullable
    static String snippet(@Nullable String text, String query) {

        if (text == null || text.isBlank()) {
            return null;
        }
        var flat = text.replaceAll("\\s+", " ").strip();
        var at = flat.toLowerCase(Locale.ROOT).indexOf(query);
        var from = Math.max(0, at < 0 ? 0 : at - AROUND);
        var to = Math.min(flat.length(), (at < 0 ? 0 : at + query.length()) + AROUND * 2);

        return (from > 0 ? "…" : "") + flat.substring(from, to).strip() + (to < flat.length() ? "…" : "");
    }
}
