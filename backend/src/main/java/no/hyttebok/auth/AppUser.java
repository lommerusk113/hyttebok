package no.hyttebok.auth;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;

import java.time.Instant;

/**
 * Someone who can log in: with Google ({@code googleSub}, Google's stable id for the account), with email and
 * password ({@code passwordHash}, see {@link Passwords}), or both. Never sent to the browser as is.
 */
@MappedEntity
public record AppUser(@Id @GeneratedValue @Nullable Long id,
                      @Nullable String googleSub,
                      String email,
                      @Nullable String name,
                      @Nullable String picture,
                      Instant createdAt,
                      Instant lastLoginAt,
                      @Nullable String passwordHash) {

    /** Google has confirmed the email belongs to this person; a password sign-up proves nothing about it. */
    public boolean emailVerified() {
        return googleSub != null;
    }

    public AppUser withPasswordHash(@Nullable String hash) {
        return new AppUser(id, googleSub, email, name, picture, createdAt, lastLoginAt, hash);
    }

    public AppUser withLogin(Instant at) {
        return new AppUser(id, googleSub, email, name, picture, createdAt, at, passwordHash);
    }
}
