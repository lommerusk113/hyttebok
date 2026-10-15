package no.hyttebok.access;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;

import java.time.Instant;

@MappedEntity
public record Invitation(@Id @GeneratedValue @Nullable Long id,
                         String token,
                         @Nullable Long createdBy,
                         Instant createdAt,
                         Instant expiresAt,
                         boolean revoked,
                         int uses) {

    boolean usable(Instant now) {
        return !revoked && expiresAt.isAfter(now);
    }
}
