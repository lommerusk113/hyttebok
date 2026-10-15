package no.hyttebok.access;

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;

@JdbcRepository(dialect = Dialect.POSTGRES)
public interface AccessRequestRepository extends CrudRepository<AccessRequest, Long> {

    List<AccessRequest> findByCabinIdAndStatusOrderByCreatedAt(Long cabinId, String status);

    Optional<AccessRequest> findFirstByCabinIdAndUserIdAndStatus(Long cabinId, Long userId, String status);

    List<AccessRequest> findByUserIdAndStatus(Long userId, String status);
}
