package no.hyttebok.mail;

import io.micronaut.context.annotation.Value;
import io.micronaut.http.context.ServerRequestContext;
import jakarta.inject.Singleton;

/** Absolute links for emails: PUBLIC_URL, or else where the request came from (the browser's Origin). */
@Singleton
public class Links {

    private final String publicUrl;

    public Links(@Value("${hyttebok.public-url:}") String publicUrl) {
        this.publicUrl = publicUrl.replaceAll("/+$", "");
    }

    public String to(String path) {

        if (!publicUrl.isBlank()) {
            return publicUrl + path;
        }
        var origin = ServerRequestContext.currentRequest().map(r -> r.getHeaders().get("Origin")).orElse(null);

        return (origin == null || origin.isBlank() ? "" : origin) + path;
    }
}
