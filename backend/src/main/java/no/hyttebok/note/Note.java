package no.hyttebok.note;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.DateUpdated;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.TenantId;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;

@Serdeable
@MappedEntity
public record Note(@Id @GeneratedValue @Nullable Long id,
                   @NotBlank String title,
                   @Nullable String content,
                   @NotBlank String color,
                   boolean pinned,
                   @DateUpdated @Nullable Instant updatedAt,
                   @TenantId @Nullable Long cabinId) {
}
