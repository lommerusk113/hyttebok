package no.hyttebok.access;

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;

@JdbcRepository(dialect = Dialect.POSTGRES)
public interface AccessRequestRepository extends CrudRepository<AccessRequest, Long> {

    List<AccessRequest> findByStatusOrderByCreatedAt(String status);

    Optional<AccessRequest> findFirstByUserIdAndStatus(Long userId, String status);
}
