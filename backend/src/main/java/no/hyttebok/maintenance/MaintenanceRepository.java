package no.hyttebok.maintenance;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;

import java.time.LocalDate;
import java.util.List;

@JdbcRepository(dialect = Dialect.POSTGRES)
public interface MaintenanceRepository extends CrudRepository<Maintenance, Long> {

    List<Maintenance> listOrderByName();

    void update(@Id Long id, @Nullable LocalDate lastCompleted);
}
