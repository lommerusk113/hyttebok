package no.hyttebok.photo;

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;

import java.util.List;

@JdbcRepository(dialect = Dialect.POSTGRES)
public interface PhotoRepository extends CrudRepository<Photo, Long> {

    /** The gallery: everything but the photos in forum posts and comments. */
    List<Photo> findByForumPostIdIsNullAndForumReplyIdIsNullOrderByCreatedAtDesc();

    List<Photo> findByForumPostIdIsNotNullOrderByCreatedAt();

    List<Photo> findByForumPostIdOrderByCreatedAt(Long forumPostId);

    List<Photo> findByForumReplyIdIsNotNullOrderByCreatedAt();

    List<Photo> findByForumReplyIdOrderByCreatedAt(Long forumReplyId);
}
