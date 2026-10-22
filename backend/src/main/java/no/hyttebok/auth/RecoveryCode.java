package no.hyttebok.auth;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;

import java.time.Instant;

/** One of the codes someone saved for themselves; only its SHA-256 is kept. Each works once. */
@MappedEntity
public record RecoveryCode(@Id @GeneratedValue @Nullable Long id, Long userId, String codeHash, @Nullable Instant usedAt) {
}
