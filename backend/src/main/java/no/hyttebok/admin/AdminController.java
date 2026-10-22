package no.hyttebok.admin;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.jdbc.runtime.JdbcOperations;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.rules.SecurityRule;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import no.hyttebok.access.AccessService;
import no.hyttebok.audit.Audit;
import no.hyttebok.auth.Passwords;
import no.hyttebok.auth.Profiles;
import no.hyttebok.chat.ChatBudget;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The admin console (system admins only): every cabin and every user, searched, filtered, sorted and paged in the
 * database so it stays quick with many of them. Each page also counts how many match each filter, for the chips.
 */
@Controller("/api/admin")
@Secured(SecurityRule.IS_AUTHENTICATED)
public class AdminController {

    private static final int PAGE_SIZE = 25;
    // Whitelists: the sort and filter go into the SQL as text, so only these values are possible.
    private static final Map<String, String> CABIN_SORT = Map.of(
        "active", "last_active DESC NULLS LAST, lower(name)",
        "name", "lower(name)",
        "members", "members DESC, lower(name)",
        "created", "created_at DESC");
    private static final Map<String, String> USER_SORT = Map.of(
        "active", "last_login_at DESC, lower(name)",
        "name", "lower(name)",
        "created", "created_at DESC");
    private static final Map<String, String> USER_FILTER = Map.of(
        "ALL", "true",
        "GOOGLE", "google",
        "PASSWORD", "password",
        "NONE", "cabins = 0");

    /*
     * One row per cabin with its members' numbers. Status, in this order: DELETED, EMPTY (no owner left), INACTIVE
     * (no member logged in for 6 months) or ACTIVE, so the chips add up to the total. The search matches the cabin's
     * name or any member's name or email.
     */
    private static final String CABINS = """
        WITH s AS (
            SELECT c.id, c.name, c.tagline, c.hero_photo_id, c.created_at, c.deleted_at, c.chat_enabled,
                   (SELECT u.cost_usd FROM chat_usage u WHERE u.cabin_id = c.id AND u.month = date_trunc('month', now() AT TIME ZONE 'Europe/Oslo')::date) AS chat_cost,
                   count(m.id) AS members,
                   count(m.id) FILTER (WHERE m.role = 'OWNER') AS owner_count,
                   string_agg(coalesce(u.name, u.email), ', ' ORDER BY lower(coalesce(u.name, u.email))) FILTER (WHERE m.role = 'OWNER') AS owners,
                   max(u.last_login_at) AS last_active,
                   coalesce(bool_or(position(? IN lower(coalesce(u.name, '') || ' ' || u.email)) > 0), false) AS person_match
            FROM cabin_info c
            LEFT JOIN membership m ON m.cabin_id = c.id
            LEFT JOIN app_user u ON u.id = m.user_id
            GROUP BY c.id
        ), f AS (
            SELECT s.*, CASE WHEN deleted_at IS NOT NULL THEN 'DELETED' WHEN owner_count = 0 THEN 'EMPTY'
                             WHEN last_active IS NULL OR last_active < now() - interval '6 months' THEN 'INACTIVE' ELSE 'ACTIVE' END AS status
            FROM s WHERE position(? IN lower(name)) > 0 OR person_match
        ), g AS (
            SELECT f.*, count(*) OVER () AS n_all,
                   count(*) FILTER (WHERE status = 'ACTIVE') OVER () AS n_active,
                   count(*) FILTER (WHERE status = 'INACTIVE') OVER () AS n_inactive,
                   count(*) FILTER (WHERE status = 'EMPTY') OVER () AS n_empty,
                   count(*) FILTER (WHERE status = 'DELETED') OVER () AS n_deleted
            FROM f
        )
        SELECT g.*, count(*) OVER () AS total FROM g WHERE ? = 'ALL' OR status = ?
        ORDER BY %s LIMIT ? OFFSET ?
        """;

    private static final String USERS = """
        WITH s AS (
            SELECT u.id, coalesce(u.name, u.email) AS name, u.email, u.picture, u.google_sub IS NOT NULL AS google,
                   u.password_hash IS NOT NULL AS password, u.created_at, u.last_login_at, count(c.id) AS cabins
            FROM app_user u
            LEFT JOIN membership m ON m.user_id = u.id
            LEFT JOIN cabin_info c ON c.id = m.cabin_id AND c.deleted_at IS NULL
            WHERE position(? IN lower(coalesce(u.name, '') || ' ' || u.email)) > 0
            GROUP BY u.id
        ), g AS (
            SELECT s.*, count(*) OVER () AS n_all,
                   count(*) FILTER (WHERE google) OVER () AS n_google,
                   count(*) FILTER (WHERE password) OVER () AS n_password,
                   count(*) FILTER (WHERE cabins = 0) OVER () AS n_none
            FROM s
        )
        SELECT g.*, count(*) OVER () AS total FROM g WHERE %s
        ORDER BY %s LIMIT ? OFFSET ?
        """;

    private final JdbcOperations jdbc;
    private final AccessService access;
    private final Profiles profiles;
    private final ChatBudget budget;
    private final Audit audit;

    public AdminController(JdbcOperations jdbc, AccessService access, Profiles profiles, ChatBudget budget, Audit audit) {
        this.jdbc = jdbc;
        this.access = access;
        this.profiles = profiles;
        this.budget = budget;
        this.audit = audit;
    }

    /** {@code counts}: how many match the search for each filter (ALL, ACTIVE, ... for cabins; ALL, GOOGLE, ... for users). */
    @Serdeable
    public record Page<T>(List<T> items, int total, int pageSize, Map<String, Integer> counts) {
    }

    @Serdeable
    public record CabinRow(Long id, String name, @Nullable String tagline, @Nullable Long heroPhotoId, @Nullable String owners, int members,
                           @Nullable Instant lastActive, Instant createdAt, String status, boolean chatEnabled, int chatPercent) {
    }

    @Serdeable
    public record UserRow(Long id, String name, String email, @Nullable String picture, boolean google, boolean password, boolean admin,
                          int cabins, Instant createdAt, Instant lastLoginAt) {
    }

    @Serdeable
    public record NewPassword(@NotBlank @Size(min = Passwords.MIN_LENGTH, max = Passwords.MAX_LENGTH) String password) {
    }

    /** {@code status}: ALL, ACTIVE, INACTIVE, EMPTY or DELETED. {@code sort}: active, name, members or created. */
    @Get("/cabins")
    @Transactional(readOnly = true)
    public Page<CabinRow> cabins(Authentication auth, @QueryValue(defaultValue = "") String q, @QueryValue(defaultValue = "ALL") String status,
                                 @QueryValue(defaultValue = "active") String sort, @QueryValue(defaultValue = "0") int page) {

        access.requireAdmin(auth);
        var search = search(q);
        var sql = CABINS.formatted(CABIN_SORT.getOrDefault(sort, CABIN_SORT.get("active")));

        return jdbc.prepareStatement(sql, statement -> {
            statement.setString(1, search);
            statement.setString(2, search);
            statement.setString(3, status);
            statement.setString(4, status);
            statement.setInt(5, PAGE_SIZE);
            statement.setInt(6, offset(page));
            var rows = new ArrayList<CabinRow>();
            var counts = Map.<String, Integer>of();
            var total = 0;
            try (var rs = statement.executeQuery()) {
                while (rs.next()) {
                    rows.add(new CabinRow(rs.getLong("id"), rs.getString("name"), rs.getString("tagline"), rs.getObject("hero_photo_id", Long.class),
                        rs.getString("owners"), rs.getInt("members"), instant(rs, "last_active"), instant(rs, "created_at"), rs.getString("status"),
                        rs.getBoolean("chat_enabled"), budget.percent(rs.getDouble("chat_cost"))));
                    total = rs.getInt("total");
                    counts = Map.of("ALL", rs.getInt("n_all"), "ACTIVE", rs.getInt("n_active"), "INACTIVE", rs.getInt("n_inactive"),
                        "EMPTY", rs.getInt("n_empty"), "DELETED", rs.getInt("n_deleted"));
                }
            }
            return new Page<>(rows, total, PAGE_SIZE, counts);
        });
    }

    /** {@code filter}: ALL, GOOGLE, PASSWORD or NONE (in no cabin). {@code sort}: active, name or created. */
    @Get("/users")
    @Transactional(readOnly = true)
    public Page<UserRow> users(Authentication auth, @QueryValue(defaultValue = "") String q, @QueryValue(defaultValue = "ALL") String filter,
                               @QueryValue(defaultValue = "active") String sort, @QueryValue(defaultValue = "0") int page) {

        access.requireAdmin(auth);
        var sql = USERS.formatted(USER_FILTER.getOrDefault(filter, "true"), USER_SORT.getOrDefault(sort, USER_SORT.get("active")));

        return jdbc.prepareStatement(sql, statement -> {
            statement.setString(1, search(q));
            statement.setInt(2, PAGE_SIZE);
            statement.setInt(3, offset(page));
            var rows = new ArrayList<UserRow>();
            var counts = Map.<String, Integer>of();
            var total = 0;
            try (var rs = statement.executeQuery()) {
                while (rs.next()) {
                    var email = rs.getString("email");
                    rows.add(new UserRow(rs.getLong("id"), rs.getString("name"), email, rs.getString("picture"), rs.getBoolean("google"), rs.getBoolean("password"),
                        rs.getBoolean("google") && access.isAdmin(email), rs.getInt("cabins"), instant(rs, "created_at"), instant(rs, "last_login_at")));
                    total = rs.getInt("total");
                    counts = Map.of("ALL", rs.getInt("n_all"), "GOOGLE", rs.getInt("n_google"), "PASSWORD", rs.getInt("n_password"), "NONE", rs.getInt("n_none"));
                }
            }
            return new Page<>(rows, total, PAGE_SIZE, counts);
        });
    }

    @Get("/users/{id}")
    public Profiles.Profile user(Authentication auth, Long id) {
        access.requireAdmin(auth);
        return profiles.profile(id, true);
    }

    @Post("/users/{id}/password-reset")
    public Profiles.ResetLink resetLink(Authentication auth, Long id) {
        access.requireAdmin(auth);
        audit.log(null, auth, Audit.RESET_LINK, id, null);

        return profiles.resetLink(id, AccessService.userId(auth));
    }

    /** For helping someone on the phone: the admin picks the password and tells them. */
    @Put("/users/{id}/password")
    public void setPassword(Authentication auth, Long id, @Body @Valid NewPassword body) {
        access.requireAdmin(auth);
        profiles.setPassword(id, body.password());
        audit.log(null, auth, Audit.PASSWORD_SET, id, null);
    }

    /** The change log of every cabin and account, newest first. */
    @Get("/log")
    public Page<Audit.Event> log(Authentication auth, @QueryValue(defaultValue = "0") int page) {

        access.requireAdmin(auth);
        var events = audit.all(PAGE_SIZE, offset(page));

        return new Page<>(events.items(), events.total(), PAGE_SIZE, Map.of());
    }

    /** Lower case, matched with position() rather than LIKE, so "%" and "_" are just characters. */
    private static String search(String q) {
        return q.trim().toLowerCase(Locale.ROOT);
    }

    private static int offset(int page) {
        return Math.max(0, page) * PAGE_SIZE;
    }

    @Nullable
    private static Instant instant(ResultSet rs, String column) throws SQLException {
        var value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
