package no.hyttebok.access;

import io.micronaut.data.annotation.Query;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;

@JdbcRepository(dialect = Dialect.POSTGRES)
public interface MembershipRepository extends CrudRepository<Membership, Long> {

    Optional<Membership> findByUserIdAndCabinId(Long userId, Long cabinId);

    /** The membership, unless the cabin is deleted (then nobody is let in; admins in admin mode excepted). */
    @Query("SELECT m.* FROM membership m JOIN cabin_info c ON c.id = m.cabin_id WHERE m.user_id = :userId AND m.cabin_id = :cabinId AND c.deleted_at IS NULL")
    Optional<Membership> findActive(Long userId, Long cabinId);

    List<Membership> findByUserId(Long userId);

    List<Membership> findByCabinId(Long cabinId);

    boolean existsByUserIdAndCabinId(Long userId, Long cabinId);

    void deleteByUserIdAndCabinId(Long userId, Long cabinId);
}
