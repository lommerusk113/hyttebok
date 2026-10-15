package no.hyttebok.forum;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.Valid;
import no.hyttebok.push.Notifier;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/** The cabin's forum: posts and replies, searchable and sortable. */
@Controller("/api/forum")
public class ForumController {

    private final ForumPostRepository posts;
    private final ForumReplyRepository replies;
    private final Notifier notifier;

    public ForumController(ForumPostRepository posts, ForumReplyRepository replies, Notifier notifier) {
        this.posts = posts;
        this.replies = replies;
        this.notifier = notifier;
    }

    /** A post in the list: how many replies, and the latest one. */
    @Serdeable
    public record Summary(ForumPost post, int replies, @Nullable Instant lastReplyAt, @Nullable String lastReplyBy) {

        Instant lastActivity() {
            return lastReplyAt == null ? post.createdAt() : lastReplyAt;
        }
    }

    @Serdeable
    public record Thread(ForumPost post, List<ForumReply> replies) {
    }

    /**
     * {@code q} matches title, text, author and the replies' text (any case). {@code sort}: {@code active} (latest
     * reply or new post first, the default), {@code new}, {@code old} or {@code replies} (most first).
     */
    @Get
    public List<Summary> list(@Nullable @QueryValue String q, @Nullable @QueryValue String sort) {

        // ponytail: the cabin's whole forum in memory; fine for thousands of posts. Full-text search in SQL if it grows.
        var byPost = replies.listOrderByCreatedAt().stream().collect(Collectors.groupingBy(ForumReply::postId));
        var needle = q == null || q.isBlank() ? null : q.trim().toLowerCase(Locale.ROOT);

        var summaries = posts.listOrderByCreatedAtDesc().stream()
            .filter(p -> needle == null || matches(p, byPost.getOrDefault(p.id(), List.of()), needle))
            .map(p -> summary(p, byPost.getOrDefault(p.id(), List.of())));

        Comparator<Summary> order = switch (sort == null ? "active" : sort) {
            case "new" -> Comparator.comparing((Summary s) -> s.post().createdAt()).reversed();
            case "old" -> Comparator.comparing((Summary s) -> s.post().createdAt());
            case "replies" -> Comparator.comparingInt(Summary::replies).reversed().thenComparing(Summary::lastActivity, Comparator.reverseOrder());
            default -> Comparator.comparing(Summary::lastActivity).reversed();
        };

        return summaries.sorted(order).toList();
    }

    @Get("/{id}")
    public Thread get(Long id) {
        return new Thread(post(id), replies.findByPostIdOrderByCreatedAt(id));
    }

    @Post
    public ForumPost create(@Body @Valid ForumPost post) {

        var saved = posts.save(new ForumPost(null, post.title().trim(), blankToNull(post.body()), post.author(), null, null, null));
        notifier.forumPostCreated(saved);

        return saved;
    }

    /** New title and text; who wrote it and when stay as they were. */
    @Put("/{id}")
    public ForumPost update(Long id, @Body @Valid ForumPost post) {

        var old = post(id);

        return posts.update(new ForumPost(old.id(), post.title().trim(), blankToNull(post.body()), old.author(), old.createdAt(), Instant.now(), old.cabinId()));
    }

    @Delete("/{id}")
    public void delete(Long id) {
        posts.deleteById(post(id).id());
    }

    @Post("/{id}/replies")
    public ForumReply reply(Long id, @Body @Valid ForumReply reply) {

        var post = post(id);
        var earlier = replies.findByPostIdOrderByCreatedAt(id).stream().map(ForumReply::author).toList();
        var saved = replies.save(new ForumReply(null, post.id(), reply.body().trim(), reply.author(), null, null, null));
        notifier.forumReplied(post, saved, earlier);

        return saved;
    }

    @Put("/replies/{id}")
    public ForumReply updateReply(Long id, @Body @Valid ForumReply reply) {

        var old = replies.findById(id).orElseThrow(() -> new HttpStatusException(HttpStatus.NOT_FOUND, "No such reply"));

        return replies.update(new ForumReply(old.id(), old.postId(), reply.body().trim(), old.author(), old.createdAt(), Instant.now(), old.cabinId()));
    }

    @Delete("/replies/{id}")
    public void deleteReply(Long id) {
        replies.deleteById(id);
    }

    private ForumPost post(Long id) {
        return posts.findById(id).orElseThrow(() -> new HttpStatusException(HttpStatus.NOT_FOUND, "No such post"));
    }

    private static boolean matches(ForumPost post, List<ForumReply> replies, String needle) {
        return contains(post.title(), needle) || contains(post.body(), needle) || contains(post.author(), needle)
            || replies.stream().anyMatch(r -> contains(r.body(), needle) || contains(r.author(), needle));
    }

    private static boolean contains(@Nullable String text, String needle) {
        return text != null && text.toLowerCase(Locale.ROOT).contains(needle);
    }

    private static Summary summary(ForumPost post, List<ForumReply> replies) {

        var last = replies.isEmpty() ? null : replies.getLast();

        return new Summary(post, replies.size(), last == null ? null : last.createdAt(), last == null ? null : last.author());
    }

    @Nullable
    private static String blankToNull(@Nullable String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }
}
