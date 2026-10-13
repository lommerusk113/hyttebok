package no.hyttebok.booking;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

@Serdeable
@MappedEntity
public record Booking(@Id @GeneratedValue @Nullable Long id,
                      @NotBlank String guestName,
                      @NotNull LocalDate startDate,
                      @NotNull LocalDate endDate,
                      @Nullable String note) {
}
