package no.hyttebok.checklist;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.DateCreated;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.TenantId;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/** Something that should always be at the cabin. Same name twice is refused (409), whatever the case. */
@Serdeable
@MappedEntity
public record ChecklistItem(@Id @GeneratedValue @Nullable Long id,
                            @NotBlank @Size(max = 100) String name,
                            @DateCreated @Nullable Instant createdAt,
                            @TenantId @Nullable Long cabinId) {
}
