package no.hyttebok.auth;

import io.micronaut.data.annotation.Query;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@JdbcRepository(dialect = Dialect.POSTGRES)
public interface AppUserRepository extends CrudRepository<AppUser, Long> {

    Optional<AppUser> findByGoogleSub(String googleSub);

    List<AppUser> findByIdIn(Collection<Long> ids);

    /** Case-insensitive exact match (not LIKE: "_" in an address must not act as a wildcard). */
    @Query("SELECT * FROM app_user WHERE lower(email) = lower(:email)")
    Optional<AppUser> findByEmail(String email);
}
