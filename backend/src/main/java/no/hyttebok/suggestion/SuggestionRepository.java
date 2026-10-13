package no.hyttebok.suggestion;

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;

import java.util.List;

@JdbcRepository(dialect = Dialect.POSTGRES)
public interface SuggestionRepository extends CrudRepository<Suggestion, Long> {

    List<Suggestion> listOrderByCreatedAtDesc();
}
