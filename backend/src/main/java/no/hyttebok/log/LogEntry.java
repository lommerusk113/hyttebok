package no.hyttebok.log;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.DateCreated;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.TenantId;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.DataType;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * A cabin book post. Its photos point back to it via {@code photo.log_entry_id}.
 * Title is nullable in the database only for posts written before titles existed.
 */
@Serdeable
@MappedEntity
public record LogEntry(@Id @GeneratedValue @Nullable Long id,
                       @NotBlank String author,
                       @NotBlank String title,
                       @Nullable String content,
                       @NotNull @TypeDef(type = DataType.STRING_ARRAY) List<String> participants,
                       @Nullable LocalDate startDate,
                       @Nullable LocalDate endDate,
                       @Nullable Long bookingId,
                       @DateCreated @Nullable Instant createdAt,
                       @TenantId @Nullable Long cabinId) {
}
