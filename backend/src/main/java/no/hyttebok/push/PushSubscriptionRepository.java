package no.hyttebok.push;

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;

import java.util.Optional;

@JdbcRepository(dialect = Dialect.POSTGRES)
public interface PushSubscriptionRepository extends CrudRepository<PushSubscription, Long> {

    Optional<PushSubscription> findByEndpoint(String endpoint);

    void deleteByEndpoint(String endpoint);
}
