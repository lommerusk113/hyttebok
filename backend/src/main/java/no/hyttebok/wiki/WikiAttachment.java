package no.hyttebok.wiki;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.DateCreated;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.TenantId;
import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;

/** A photo or PDF on a wiki page. {@code originalName}: what it was called when uploaded (shown, and used for downloads). */
@Serdeable
@MappedEntity
public record WikiAttachment(@Id @GeneratedValue @Nullable Long id,
                             Long wikiPageId,
                             String fileName,
                             String contentType,
                             String originalName,
                             long size,
                             @Nullable String uploadedBy,
                             @DateCreated @Nullable Instant createdAt,
                             @TenantId @Nullable Long cabinId) {
}
