package no.hyttebok.forum;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.DateCreated;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.TenantId;
import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;

/** Someone's reaction to a post ({@code postId}) or a comment ({@code replyId}). */
@Serdeable
@MappedEntity
public record ForumReaction(@Id @GeneratedValue @Nullable Long id,
                            @Nullable Long postId,
                            @Nullable Long replyId,
                            String emoji,
                            String author,
                            @DateCreated @Nullable Instant createdAt,
                            @TenantId @Nullable Long cabinId) {
}
