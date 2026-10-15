package no.hyttebok.auth;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;

import java.time.Instant;

/** A one-time "new password" link, made by an owner or admin and valid for a day. */
@MappedEntity
public record PasswordReset(@Id String token, Long userId, @Nullable Long createdBy, Instant expiresAt, boolean used) {

    public boolean usable(Instant now) {
        return !used && expiresAt.isAfter(now);
    }
}
