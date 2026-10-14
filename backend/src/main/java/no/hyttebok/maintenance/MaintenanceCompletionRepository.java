package no.hyttebok.maintenance;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;

import java.time.LocalDate;
import java.util.List;

@JdbcRepository(dialect = Dialect.POSTGRES)
public interface MaintenanceCompletionRepository extends CrudRepository<MaintenanceCompletion, Long> {

    List<MaintenanceCompletion> listOrderByCompletedOnDesc();

    @Nullable
    LocalDate findMaxCompletedOnByMaintenanceId(Long maintenanceId);
}
