package no.hyttebok.auth;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;

@MappedEntity
public record AppSecret(@Id String name, String value) {
}
