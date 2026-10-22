package no.hyttebok.wiki;

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;

import java.util.List;

@JdbcRepository(dialect = Dialect.POSTGRES)
public interface WikiAttachmentRepository extends CrudRepository<WikiAttachment, Long> {

    List<WikiAttachment> listOrderByCreatedAt();

    List<WikiAttachment> findByWikiPageId(Long wikiPageId);
}
