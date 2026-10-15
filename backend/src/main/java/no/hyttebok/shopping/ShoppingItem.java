package no.hyttebok.shopping;

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
public record ShoppingItem(@Id @GeneratedValue @Nullable Long id,
                           @NotBlank String name,
                           @Nullable String quantity,
                           boolean bought,
                           @Nullable String boughtBy,
                           @Nullable Instant boughtAt,
                           @Nullable String addedBy,
                           @DateCreated @Nullable Instant createdAt,
                           @TenantId @Nullable Long cabinId) {
}
