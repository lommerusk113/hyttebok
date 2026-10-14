package no.hyttebok.maintenance;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.DateCreated;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.time.Instant;
import java.time.LocalDate;

/**
 * A recurring maintenance task. The next due date is derived in the app:
 * (lastCompleted, or the day it was created) + interval.
 */
@Serdeable
@MappedEntity
public record Maintenance(@Id @GeneratedValue @Nullable Long id,
                          @NotBlank String name,
                          @Nullable String description,
                          @Positive int intervalValue,
                          @Pattern(regexp = "DAYS|WEEKS|MONTHS|YEARS") String intervalUnit,
                          @Nullable LocalDate lastCompleted,
                          @DateCreated @Nullable Instant createdAt) {
}
