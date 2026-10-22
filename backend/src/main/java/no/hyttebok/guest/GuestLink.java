package no.hyttebok.guest;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.DateCreated;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;

import java.time.Instant;
import java.time.LocalDate;

/**
 * A read-only link for someone borrowing the cabin ({@code label}: who it's for). Not filtered by the current cabin:
 * guests open it by its token, without logging in.
 */
@MappedEntity
public record GuestLink(@Id @GeneratedValue @Nullable Long id,
                        Long cabinId,
                        String token,
                        String label,
                        LocalDate validUntil,
                        boolean revoked,
                        @Nullable Long createdBy,
                        @DateCreated @Nullable Instant createdAt) {

    /** Not revoked, and today (in Norway) is no later than the last day. */
    boolean open(LocalDate today) {
        return !revoked && !today.isAfter(validUntil);
    }

    GuestLink withRevoked() {
        return new GuestLink(id, cabinId, token, label, validUntil, true, createdBy, createdAt);
    }
}
