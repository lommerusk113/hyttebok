package no.hyttebok.suggestion;

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
import jakarta.validation.constraints.Pattern;

import java.time.Instant;
import java.util.List;

@Serdeable
@MappedEntity
public record Suggestion(@Id @GeneratedValue @Nullable Long id,
                         @NotBlank String title,
                         @Nullable String description,
                         @Pattern(regexp = "DO|BUY") String category,
                         @Nullable String author,
                         @NotNull @TypeDef(type = DataType.STRING_ARRAY) List<String> voters,
                         boolean done,
                         @DateCreated @Nullable Instant createdAt,
                         @TenantId @Nullable Long cabinId) {
}
