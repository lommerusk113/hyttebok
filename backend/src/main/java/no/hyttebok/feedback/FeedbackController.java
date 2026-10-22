package no.hyttebok.feedback;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.jdbc.runtime.JdbcOperations;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.annotation.Status;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.rules.SecurityRule;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import no.hyttebok.access.AccessService;
import no.hyttebok.cabin.CabinContext;

import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** "Gi tilbakemelding": anyone logged in can send a message; system admins read them in the admin console. */
@Controller("/api/feedback")
@Secured(SecurityRule.IS_AUTHENTICATED)
public class FeedbackController {

    private static final int PAGE_SIZE = 25;

    private final JdbcOperations jdbc;
    private final AccessService access;

    public FeedbackController(JdbcOperations jdbc, AccessService access) {
        this.jdbc = jdbc;
        this.access = access;
    }

    /** {@code page}: where in the app it was sent from (the path), to know what it's about. */
    @Serdeable
    public record NewFeedback(@NotBlank @Size(max = 4000) String message, @Nullable @Size(max = 200) String page) {
    }

    @Serdeable
    public record Feedback(Long id, @Nullable String userName, @Nullable String email, @Nullable String cabinName, @Nullable String page, String message,
                           Instant createdAt, boolean handled) {
    }

    @Serdeable
    public record Feedbacks(List<Feedback> items, int total, int pageSize, int open) {
    }

    @Serdeable
    public record Handled(boolean handled) {
    }

    @Post
    @Status(HttpStatus.CREATED)
    @Transactional
    public void send(Authentication auth, @Body @Valid NewFeedback body) {

        var cabin = CabinContext.current().orElse(null);
        jdbc.prepareStatement("INSERT INTO feedback (user_id, user_name, cabin_id, page, message) VALUES (?, ?, ?, ?, ?)", statement -> {
            statement.setLong(1, AccessService.userId(auth));
            statement.setString(2, String.valueOf(auth.getAttributes().get("name")));
            if (cabin == null) {
                statement.setNull(3, Types.BIGINT);
            } else {
                statement.setLong(3, cabin);
            }
            statement.setString(4, body.page());
            statement.setString(5, body.message().trim());
            return statement.executeUpdate();
        });
    }

    /** System admins: newest first, unhandled before handled. */
    @Get
    @Transactional(readOnly = true)
    public Feedbacks list(Authentication auth, @QueryValue(defaultValue = "0") int page) {

        access.requireAdmin(auth);

        return jdbc.prepareStatement("""
            SELECT f.*, u.email, c.name AS cabin_name, count(*) OVER () AS total, count(*) FILTER (WHERE NOT f.handled) OVER () AS open
            FROM feedback f LEFT JOIN app_user u ON u.id = f.user_id LEFT JOIN cabin_info c ON c.id = f.cabin_id
            ORDER BY f.handled, f.created_at DESC LIMIT ? OFFSET ?
            """, statement -> {

            statement.setInt(1, PAGE_SIZE);
            statement.setInt(2, Math.max(0, page) * PAGE_SIZE);
            var items = new ArrayList<Feedback>();
            var total = 0;
            var open = 0;
            try (var rs = statement.executeQuery()) {
                while (rs.next()) {
                    items.add(new Feedback(rs.getLong("id"), rs.getString("user_name"), rs.getString("email"), rs.getString("cabin_name"), rs.getString("page"),
                        rs.getString("message"), rs.getTimestamp("created_at").toInstant(), rs.getBoolean("handled")));
                    total = rs.getInt("total");
                    open = rs.getInt("open");
                }
            }

            return new Feedbacks(items, total, PAGE_SIZE, open);
        });
    }

    @Put("/{id}")
    @Transactional
    public void setHandled(Authentication auth, Long id, @Body Handled body) {

        access.requireAdmin(auth);
        jdbc.prepareStatement("UPDATE feedback SET handled = ? WHERE id = ?", statement -> {
            statement.setBoolean(1, body.handled());
            statement.setLong(2, id);
            return statement.executeUpdate();
        });
    }
}
