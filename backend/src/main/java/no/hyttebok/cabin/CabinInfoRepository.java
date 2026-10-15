package no.hyttebok.cabin;

import io.micronaut.data.annotation.Query;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Cabins. The join code is a column the entity doesn't carry (so it never reaches the app with the cabin info);
 * these queries read and change it on purpose.
 */
@JdbcRepository(dialect = Dialect.POSTGRES)
public interface CabinInfoRepository extends CrudRepository<CabinInfo, Long> {

    List<CabinInfo> findByIdInOrderByName(Collection<Long> ids);

    List<CabinInfo> listOrderByName();

    @Query("SELECT join_code FROM cabin_info WHERE id = :id")
    Optional<String> findJoinCode(Long id);

    @Query("UPDATE cabin_info SET join_code = :code WHERE id = :id")
    void updateJoinCode(Long id, String code);

    @Query("SELECT id FROM cabin_info WHERE join_code = :code")
    Optional<Long> findIdByJoinCode(String code);
}
