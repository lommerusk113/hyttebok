package no.hyttebok.forum;

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

@Serdeable
@MappedEntity
public record ForumReply(@Id @GeneratedValue @Nullable Long id,
                         @Nullable Long postId,
                         @NotBlank @Size(max = 10000) String body,
                         @Nullable String author,
                         @DateCreated @Nullable Instant createdAt,
                         @Nullable Instant editedAt,
                         @TenantId @Nullable Long cabinId) {
}
