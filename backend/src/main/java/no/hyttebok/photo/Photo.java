package no.hyttebok.photo;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.DateCreated;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.TenantId;
import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;

@Serdeable
@MappedEntity
public record Photo(@Id @GeneratedValue @Nullable Long id,
                    String fileName,
                    String contentType,
                    @Nullable String caption,
                    @Nullable String uploadedBy,
                    @Nullable Long logEntryId,
                    @Nullable Long forumPostId,
                    @Nullable Long forumReplyId,
                    @DateCreated @Nullable Instant createdAt,
                    @TenantId @Nullable Long cabinId) {
}
