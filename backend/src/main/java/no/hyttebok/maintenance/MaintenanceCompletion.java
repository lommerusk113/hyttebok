package no.hyttebok.maintenance;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.DateCreated;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.time.LocalDate;

@Serdeable
@MappedEntity
public record MaintenanceCompletion(@Id @GeneratedValue @Nullable Long id,
                                    @NotNull Long maintenanceId,
                                    @NotNull LocalDate completedOn,
                                    @Nullable String completedBy,
                                    @DateCreated @Nullable Instant createdAt) {
}
