package no.hyttebok.access;

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;

@JdbcRepository(dialect = Dialect.POSTGRES)
public interface MembershipRepository extends CrudRepository<Membership, Long> {

    Optional<Membership> findByUserIdAndCabinId(Long userId, Long cabinId);

    List<Membership> findByUserId(Long userId);

    List<Membership> findByCabinId(Long cabinId);

    boolean existsByUserIdAndCabinId(Long userId, Long cabinId);

    void deleteByUserIdAndCabinId(Long userId, Long cabinId);
}
