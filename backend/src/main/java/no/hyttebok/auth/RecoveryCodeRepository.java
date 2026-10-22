package no.hyttebok.auth;

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;

import java.util.Optional;

@JdbcRepository(dialect = Dialect.POSTGRES)
public interface RecoveryCodeRepository extends CrudRepository<RecoveryCode, Long> {

    Optional<RecoveryCode> findByUserIdAndCodeHashAndUsedAtIsNull(Long userId, String codeHash);

    long countByUserIdAndUsedAtIsNull(Long userId);

    void deleteByUserId(Long userId);
}
