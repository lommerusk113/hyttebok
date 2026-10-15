package no.hyttebok.forum;

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;

import java.util.List;

@JdbcRepository(dialect = Dialect.POSTGRES)
public interface ForumReactionRepository extends CrudRepository<ForumReaction, Long> {

    List<ForumReaction> listOrderByCreatedAt();

    List<ForumReaction> findByPostIdOrderByCreatedAt(Long postId);

    List<ForumReaction> findByReplyIdOrderByCreatedAt(Long replyId);
}
