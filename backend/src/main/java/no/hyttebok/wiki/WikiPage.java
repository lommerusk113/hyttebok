package no.hyttebok.wiki;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.DateUpdated;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.TenantId;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

@Serdeable
@MappedEntity
public record WikiPage(@Id @GeneratedValue @Nullable Long id,
                       @NotBlank @Size(max = 200) String title,
                       @Nullable @Size(max = 20000) String content,
                       @Nullable String updatedBy,
                       @DateUpdated @Nullable Instant updatedAt,
                       @TenantId @Nullable Long cabinId) {
}
