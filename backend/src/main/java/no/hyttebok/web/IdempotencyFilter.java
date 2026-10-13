package no.hyttebok.web;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ResponseFilter;
import io.micronaut.http.annotation.ServerFilter;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Makes replayed offline changes safe. The app sends an {@code Idempotency-Key} with every queued
 * change; if the server already handled that key (the reply got lost on a bad connection and the
 * app retried), the original reply is returned instead of doing the change twice.
 */
@ServerFilter("/api/**")
public class IdempotencyFilter {

    private static final String HEADER = "Idempotency-Key";
    private static final int MAX_KEYS = 2000;

    // ponytail: in memory, so a backend restart forgets keys; a table if retries across restarts matter
    private final Map<String, Replay> seen = Collections.synchronizedMap(new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Replay> eldest) {
            return size() > MAX_KEYS;
        }
    });

    @RequestFilter
    @Nullable
    public HttpResponse<?> replay(HttpRequest<?> request) {

        var key = request.getHeaders().get(HEADER);
        var previous = key == null ? null : seen.get(key);

        return previous == null ? null : HttpResponse.status(previous.status()).body(previous.body());
    }

    @ResponseFilter
    public void remember(HttpRequest<?> request, MutableHttpResponse<?> response) {

        var key = request.getHeaders().get(HEADER);
        if (key != null && response.getStatus().getCode() < 300) {
            seen.put(key, new Replay(response.getStatus(), response.getBody().orElse(null)));
        }
    }

    private record Replay(HttpStatus status, @Nullable Object body) {
    }
}
