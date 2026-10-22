package no.hyttebok.audit;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.jdbc.runtime.JdbcOperations;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;
import no.hyttebok.auth.AppUserRepository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * The change log: who changed what about access (roles, removals, invitations, deleted cabins, passwords). Owners see
 * their cabin's events on "Tilgang"; system admins see everything in the admin console, including what they did in
 * admin mode. Names are copied in, so old events still read right.
 */
@Singleton
public class Audit {

    public static final String ROLE_CHANGED = "ROLE_CHANGED";
    public static final String MEMBER_REMOVED = "MEMBER_REMOVED";
    public static final String MEMBER_LEFT = "MEMBER_LEFT";
    public static final String MEMBER_JOINED = "MEMBER_JOINED";
    public static final String ADMIN_JOINED = "ADMIN_JOINED";
    public static final String INVITE_CREATED = "INVITE_CREATED";
    public static final String INVITE_REVOKED = "INVITE_REVOKED";
    public static final String CABIN_CREATED = "CABIN_CREATED";
    public static final String CABIN_DELETED = "CABIN_DELETED";
    public static final String CABIN_RESTORED = "CABIN_RESTORED";
    public static final String CHAT_CHANGED = "CHAT_CHANGED";
    public static final String RESET_LINK = "RESET_LINK";
    public static final String PASSWORD_SET = "PASSWORD_SET";
    public static final String PASSWORD_RESET = "PASSWORD_RESET";
    public static final String RECOVERY_USED = "RECOVERY_USED";
    public static final String SIGNED_OUT_EVERYWHERE = "SIGNED_OUT_EVERYWHERE";
    public static final String ACCOUNT_DELETED = "ACCOUNT_DELETED";
    public static final String EXPORTED = "EXPORTED";

    private static final String SELECT = """
        SELECT e.*, c.name AS cabin_name, count(*) OVER () AS total
        FROM audit_event e LEFT JOIN cabin_info c ON c.id = e.cabin_id
        """;

    private final JdbcOperations jdbc;
    private final AppUserRepository users;

    public Audit(JdbcOperations jdbc, AppUserRepository users) {
        this.jdbc = jdbc;
        this.users = users;
    }

    /** {@code actor}/{@code target} hold the names as they were; {@code detail} is e.g. the new role. */
    @Serdeable
    public record Event(Long id, @Nullable Long cabinId, @Nullable String cabinName, @Nullable Long actorId, @Nullable String actor, String action,
                        @Nullable Long targetId, @Nullable String target, @Nullable String detail, Instant at) {
    }

    @Serdeable
    public record Events(List<Event> items, int total) {
    }

    /**
     * Records an event. {@code actor} null = nobody logged in (e.g. a reset link being used: then pass the target as actor).
     */
    @Transactional
    public void log(@Nullable Long cabinId, @Nullable Authentication actor, String action, @Nullable Long targetId, @Nullable String detail) {

        var actorId = actor == null ? null : ((Number) actor.getAttributes().get("userId")).longValue();
        var actorName = actor == null ? null : String.valueOf(actor.getAttributes().get("name"));
        var targetName = targetId == null ? null : users.findById(targetId).map(u -> u.name() == null ? u.email() : u.name()).orElse(null);

        jdbc.prepareStatement("INSERT INTO audit_event (cabin_id, actor_id, actor_name, action, target_id, target_name, detail) VALUES (?, ?, ?, ?, ?, ?, ?)",
            statement -> {
                setLong(statement, 1, cabinId);
                setLong(statement, 2, actorId);
                statement.setString(3, actorName);
                statement.setString(4, action);
                setLong(statement, 5, targetId);
                statement.setString(6, targetName);
                statement.setString(7, detail);
                return statement.executeUpdate();
            });
    }

    /** The newest events of one cabin. */
    @Transactional(readOnly = true)
    public List<Event> forCabin(Long cabinId, int limit) {
        return query(SELECT + " WHERE e.cabin_id = ? ORDER BY e.created_at DESC, e.id DESC LIMIT ?", cabinId, limit, 0).items();
    }

    /** Everything (admins), newest first, a page at a time. */
    @Transactional(readOnly = true)
    public Events all(int limit, int offset) {
        return query(SELECT + " ORDER BY e.created_at DESC, e.id DESC LIMIT ? OFFSET ?", null, limit, offset);
    }

    private Events query(String sql, @Nullable Long cabinId, int limit, int offset) {
        return jdbc.prepareStatement(sql, statement -> {

            var i = 1;
            if (cabinId != null) {
                statement.setLong(i++, cabinId);
            }
            statement.setInt(i++, limit);
            if (cabinId == null) {
                statement.setInt(i, offset);
            }

            var events = new ArrayList<Event>();
            var total = 0;
            try (var rs = statement.executeQuery()) {
                while (rs.next()) {
                    events.add(new Event(rs.getLong("id"), nullableLong(rs, "cabin_id"), rs.getString("cabin_name"), nullableLong(rs, "actor_id"), rs.getString("actor_name"),
                        rs.getString("action"), nullableLong(rs, "target_id"), rs.getString("target_name"), rs.getString("detail"),
                        rs.getTimestamp("created_at").toInstant()));
                    total = rs.getInt("total");
                }
            }

            return new Events(events, total);
        });
    }

    private static void setLong(PreparedStatement statement, int index, @Nullable Long value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.BIGINT);
        } else {
            statement.setLong(index, value);
        }
    }

    @Nullable
    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, Long.class);
    }
}
