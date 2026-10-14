package no.hyttebok.shopping;

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;

import java.time.Instant;
import java.util.List;

@JdbcRepository(dialect = Dialect.POSTGRES)
public interface ShoppingItemRepository extends CrudRepository<ShoppingItem, Long> {

    List<ShoppingItem> listOrderByCreatedAtDesc();

    List<ShoppingItem> findByCreatedAtGreaterThan(Instant since);
}
