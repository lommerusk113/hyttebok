package no.hyttebok.wiki;

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;

import java.util.List;

@JdbcRepository(dialect = Dialect.POSTGRES)
public interface WikiPageRepository extends CrudRepository<WikiPage, Long> {

    List<WikiPage> listOrderByTitle();
}
