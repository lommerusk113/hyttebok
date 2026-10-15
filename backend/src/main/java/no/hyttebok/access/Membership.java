package no.hyttebok.access;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;

import java.time.Instant;

/**
 * A user's access to one cabin. {@code role} is OWNER (can invite and approve) or MEMBER. Not filtered by the
 * current cabin on purpose: "which cabins am I in" looks across all of them.
 */
@MappedEntity
public record Membership(@Id @GeneratedValue @Nullable Long id, Long userId, Long cabinId, String role, Instant createdAt) {

    public static final String OWNER = "OWNER";
    public static final String MEMBER = "MEMBER";

    public Membership withRole(String newRole) {
        return new Membership(id, userId, cabinId, newRole, createdAt);
    }
}
