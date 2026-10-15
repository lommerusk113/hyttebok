package no.hyttebok.forum;

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;

import java.util.List;

@JdbcRepository(dialect = Dialect.POSTGRES)
public interface ForumReplyRepository extends CrudRepository<ForumReply, Long> {

    List<ForumReply> listOrderByCreatedAt();

    List<ForumReply> findByPostIdOrderByCreatedAt(Long postId);
}
