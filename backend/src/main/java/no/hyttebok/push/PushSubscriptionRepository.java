package no.hyttebok.push;

import io.micronaut.data.annotation.Query;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;

@JdbcRepository(dialect = Dialect.POSTGRES)
public interface PushSubscriptionRepository extends CrudRepository<PushSubscription, Long> {

    Optional<PushSubscription> findByEndpointAndCabinId(String endpoint, Long cabinId);

    List<PushSubscription> findByCabinId(Long cabinId);

    /** Devices of people who are still members of the cabin (and the cabin isn't deleted): removed members hear nothing. */
    @Query("""
        SELECT s.* FROM push_subscription s
        JOIN membership m ON m.user_id = s.user_id AND m.cabin_id = s.cabin_id
        JOIN cabin_info c ON c.id = s.cabin_id AND c.deleted_at IS NULL
        WHERE s.cabin_id = :cabinId
        """)
    List<PushSubscription> findMembersByCabinId(Long cabinId);

    void deleteByEndpointAndCabinId(String endpoint, Long cabinId);

    void deleteByUserId(Long userId);
}
