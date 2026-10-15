package no.hyttebok.cabin;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.context.ServerRequestContext;
import io.micronaut.http.exceptions.HttpStatusException;

import java.util.Optional;

/**
 * Which cabin a piece of work is for. In a request: the "X-Hytte" header the app sends (or "?hytte=" on URLs the
 * browser loads by itself, like photos). In scheduled jobs and other background work: {@link #runAs}.
 * Whether the user may use that cabin is checked separately (MemberRule).
 */
public final class CabinContext {

    public static final String HEADER = "X-Hytte";
    public static final String PARAM = "hytte";
    private static final ScopedValue<Long> CABIN = ScopedValue.newInstance();

    private CabinContext() {
    }

    /** Runs {@code work} for one cabin, e.g. a nightly job going through every cabin. */
    public static void runAs(long cabinId, Runnable work) {
        ScopedValue.where(CABIN, cabinId).run(work);
    }

    public static Optional<Long> current() {

        if (CABIN.isBound()) {
            return Optional.of(CABIN.get());
        }

        return ServerRequestContext.currentRequest().flatMap(CabinContext::fromRequest);
    }

    public static long require() {
        return current().orElseThrow(() -> new HttpStatusException(HttpStatus.BAD_REQUEST, "No cabin selected"));
    }

    public static Optional<Long> fromRequest(HttpRequest<?> request) {

        var value = request.getHeaders().get(HEADER);
        if (value == null) {
            value = request.getParameters().get(PARAM);
        }
        try {
            return value == null ? Optional.empty() : Optional.of(Long.parseLong(value.trim()));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
