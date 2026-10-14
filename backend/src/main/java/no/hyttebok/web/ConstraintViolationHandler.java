package no.hyttebok.web;

import io.micronaut.context.annotation.Requires;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import jakarta.inject.Singleton;

import java.sql.SQLException;
import java.util.Map;

/**
 * Database integrity errors (e.g. a photo for a post someone else deleted) are the client's stale data,
 * not a server fault. Answering 409 instead of 500 lets the offline queue drop the change instead of
 * retrying it forever.
 */
@Produces
@Singleton
@Requires(classes = {DataAccessException.class, ExceptionHandler.class})
public class ConstraintViolationHandler implements ExceptionHandler<DataAccessException, HttpResponse<?>> {

    // SQLState class 23 = integrity constraint violation (foreign key, unique, check, not null)
    private static final String INTEGRITY_VIOLATION = "23";

    @Override
    public HttpResponse<?> handle(HttpRequest request, DataAccessException exception) {

        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && sql.getSQLState() != null && sql.getSQLState().startsWith(INTEGRITY_VIOLATION)) {
                return HttpResponse.status(HttpStatus.CONFLICT).body(Map.of("message", "Conflicts with current data"));
            }
        }

        return HttpResponse.serverError(Map.of("message", "Database error"));
    }
}
