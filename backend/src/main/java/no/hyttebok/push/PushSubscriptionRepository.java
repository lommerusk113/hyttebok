package no.hyttebok.push;

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;

@JdbcRepository(dialect = Dialect.POSTGRES)
public interface PushSubscriptionRepository extends CrudRepository<PushSubscription, Long> {

    Optional<PushSubscription> findByEndpointAndCabinId(String endpoint, Long cabinId);

    List<PushSubscription> findByCabinId(Long cabinId);

    void deleteByEndpointAndCabinId(String endpoint, Long cabinId);
}
