package no.hyttebok.log;

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;

import java.util.List;

@JdbcRepository(dialect = Dialect.POSTGRES)
public interface LogEntryRepository extends CrudRepository<LogEntry, Long> {

    List<LogEntry> listOrderByCreatedAtDesc();
}
