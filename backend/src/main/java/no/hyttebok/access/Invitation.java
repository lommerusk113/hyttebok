package no.hyttebok.access;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;

import java.time.Instant;

@MappedEntity
public record Invitation(@Id @GeneratedValue @Nullable Long id,
                         Long cabinId,
                         String token,
                         @Nullable Long createdBy,
                         Instant createdAt,
                         Instant expiresAt,
                         boolean revoked,
                         int uses,
                         String role,
                         @Nullable Integer maxUses) {

    /** Not revoked or expired, and not used up (a single-use link works once). */
    boolean usable(Instant now) {
        return !revoked && expiresAt.isAfter(now) && (maxUses == null || uses < maxUses);
    }

    Invitation withRevoked() {
        return new Invitation(id, cabinId, token, createdBy, createdAt, expiresAt, true, uses, role, maxUses);
    }

    Invitation withUse() {
        return new Invitation(id, cabinId, token, createdBy, createdAt, expiresAt, revoked, uses + 1, role, maxUses);
    }
}
