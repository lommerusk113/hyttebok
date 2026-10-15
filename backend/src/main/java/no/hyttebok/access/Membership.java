package no.hyttebok.access;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;

import java.time.Instant;

/** A user's access to the cabin. {@code role} is OWNER (can invite and approve) or MEMBER. */
@MappedEntity
public record Membership(@Id Long userId, String role, Instant createdAt) {

    public static final String OWNER = "OWNER";
    public static final String MEMBER = "MEMBER";
}
