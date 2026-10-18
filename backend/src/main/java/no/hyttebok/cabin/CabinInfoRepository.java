package no.hyttebok.cabin;

import io.micronaut.data.annotation.Query;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;

import io.micronaut.core.annotation.Nullable;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Cabins. The join code and the chat switch are columns the entity doesn't carry (so they never reach the app
 * with the cabin info, and nobody changes them by saving the cabin); these queries read and change them on purpose.
 * So is {@code deleted_at}: a deleted cabin is only marked, see {@link #updateDeleted}.
 */
@JdbcRepository(dialect = Dialect.POSTGRES)
public interface CabinInfoRepository extends CrudRepository<CabinInfo, Long> {

    List<CabinInfo> findByIdInOrderByName(Collection<Long> ids);

    List<CabinInfo> listOrderByName();

    @Query("SELECT join_code FROM cabin_info WHERE id = :id")
    Optional<String> findJoinCode(Long id);

    @Query("UPDATE cabin_info SET join_code = :code WHERE id = :id")
    void updateJoinCode(Long id, String code);

    @Query("SELECT id FROM cabin_info WHERE join_code = :code AND deleted_at IS NULL")
    Optional<Long> findIdByJoinCode(String code);

    @Query("SELECT chat_enabled FROM cabin_info WHERE id = :id")
    Optional<Boolean> findChatEnabled(Long id);

    @Query("UPDATE cabin_info SET chat_enabled = :enabled WHERE id = :id")
    void updateChatEnabled(Long id, boolean enabled);

    @Query("SELECT id FROM cabin_info WHERE deleted_at IS NOT NULL")
    List<Long> findDeletedIds();

    @Query("SELECT deleted_at IS NOT NULL FROM cabin_info WHERE id = :id")
    Optional<Boolean> findDeleted(Long id);

    /** Marks the cabin deleted ({@code at} set) or brings it back (null): nothing is removed. */
    @Query("UPDATE cabin_info SET deleted_at = :at, deleted_by = :by WHERE id = :id")
    void updateDeleted(Long id, @Nullable Instant at, @Nullable Long by);
}
