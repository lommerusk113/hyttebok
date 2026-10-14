package no.hyttebok.push;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;

@MappedEntity
public record VapidKey(@Id Long id, String publicKey, String privateKey) {
}
