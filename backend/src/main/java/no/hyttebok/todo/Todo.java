package no.hyttebok.todo;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.DateCreated;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.TenantId;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;

@Serdeable
@MappedEntity
public record Todo(@Id @GeneratedValue @Nullable Long id,
                   @NotBlank String title,
                   @Nullable String description,
                   boolean done,
                   @Nullable String doneBy,
                   @Nullable Instant doneAt,
                   @Nullable String createdBy,
                   @DateCreated @Nullable Instant createdAt,
                   @TenantId @Nullable Long cabinId) {
}
