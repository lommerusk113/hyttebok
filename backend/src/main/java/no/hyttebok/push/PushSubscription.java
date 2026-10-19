package no.hyttebok.push;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.DateCreated;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.DataType;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;
import java.util.List;

/**
 * A device that turned on notifications. {@code name} is the person using it, so they aren't
 * notified about their own changes; {@code lang} picks the language of the texts. {@code userId} is the
 * logged-in user (set by the server, not the device), used for messages meant for owners and admins.
 */
@Serdeable
@MappedEntity
public record PushSubscription(@Id @GeneratedValue @Nullable Long id,
                               @NotBlank String endpoint,
                               @NotBlank String p256dh,
                               @NotBlank String auth,
                               @Nullable String name,
                               @Pattern(regexp = "nb|en") String lang,
                               @NotNull @TypeDef(type = DataType.STRING_ARRAY) List<@Pattern(regexp = "BOOKING|POST|SUGGESTION|TODO|SHOPPING|MAINTENANCE|FORUM") String> topics,
                               @DateCreated @Nullable Instant createdAt,
                               @Nullable Long userId,
                               @Nullable Long cabinId) {

    boolean wants(Topic topic) {
        return topics.contains(topic.name());
    }

    boolean isFrom(@Nullable String person) {
        return person != null && name != null && name.trim().equalsIgnoreCase(person.trim());
    }
}
