package no.hyttebok.cabin;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.DataType;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

@Serdeable
@MappedEntity
public record CabinInfo(@Id @GeneratedValue @Nullable Long id,
                        @NotBlank String name,
                        @Nullable String tagline,
                        @Nullable String description,
                        @Nullable String address,
                        @Nullable String wifiName,
                        @Nullable String wifiPassword,
                        @Nullable String arrivalInfo,
                        @Nullable String departureInfo,
                        @Nullable Long heroPhotoId,
                        @NotNull @TypeDef(type = DataType.STRING_ARRAY) List<String> hiddenSections,
                        @Nullable Double latitude,
                        @Nullable Double longitude,
                        @Nullable Double elevation,
                        boolean locationTyped) {
}
