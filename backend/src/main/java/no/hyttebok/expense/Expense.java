package no.hyttebok.expense;

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
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * A shared cost. EXPENSE: {@code paidBy} paid {@code amount} (in øre) and it's split evenly between
 * {@code splitBetween}. SETTLEMENT: {@code paidBy} paid back the one person in {@code splitBetween}. People are
 * names, like everywhere else in a cabin.
 */
@Serdeable
@MappedEntity
public record Expense(@Id @GeneratedValue @Nullable Long id,
                      @NotBlank @Pattern(regexp = "EXPENSE|SETTLEMENT") String kind,
                      @Nullable @Size(max = 200) String description,
                      @Positive long amount,
                      @NotBlank String paidBy,
                      @NotEmpty @TypeDef(type = DataType.STRING_ARRAY) List<@NotBlank String> splitBetween,
                      @NotNull LocalDate spentOn,
                      @Nullable String createdBy,
                      @DateCreated @Nullable Instant createdAt,
                      @TenantId @Nullable Long cabinId) {
}
