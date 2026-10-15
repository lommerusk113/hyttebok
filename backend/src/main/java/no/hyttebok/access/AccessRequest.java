package no.hyttebok.access;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;

import java.time.Instant;

@MappedEntity
public record AccessRequest(@Id @GeneratedValue @Nullable Long id,
                            Long userId,
                            @Nullable String message,
                            String status,
                            Instant createdAt,
                            @Nullable Long decidedBy,
                            @Nullable Instant decidedAt) {

    public static final String PENDING = "PENDING";
}
