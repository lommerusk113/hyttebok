package no.hyttebok.todo;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.DateCreated;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.DataType;
import jakarta.validation.constraints.Size;
import io.micronaut.data.annotation.TenantId;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.List;

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
                   @Nullable @TypeDef(type = DataType.STRING_ARRAY) List<String> doneWith,
                   @Nullable @Size(max = 2000) String doneNote,
                   @TenantId @Nullable Long cabinId) {
}
