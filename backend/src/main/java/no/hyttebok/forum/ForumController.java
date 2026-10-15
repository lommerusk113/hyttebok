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
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import no.hyttebok.push.Notifier;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** The cabin's forum as a feed: posts with their comments and reactions, searchable and sortable. */
@Controller("/api/forum")
public class ForumController {

    private final ForumPostRepository posts;
    private final ForumReplyRepository replies;
    private final ForumReactionRepository reactions;
    private final Notifier notifier;

    public ForumController(ForumPostRepository posts, ForumReplyRepository replies, ForumReactionRepository reactions, Notifier notifier) {
        this.posts = posts;
        this.replies = replies;
        this.reactions = reactions;
        this.notifier = notifier;
    }

    @Serdeable
    public record Reaction(String emoji, String author) {
    }

    @Serdeable
    public record Comment(Long id, String body, @Nullable String author, Instant createdAt, @Nullable Instant editedAt, List<Reaction> reactions) {
    }

    @Serdeable
    public record FeedPost(Long id, @Nullable String title, @Nullable String body, @Nullable String author, Instant createdAt,
                           @Nullable Instant editedAt, List<Reaction> reactions, List<Comment> comments) {

        Instant lastActivity() {
            return comments.isEmpty() ? createdAt : comments.getLast().createdAt();
        }
    }

    /** 👍 ❤️ 😂 😮 😢 🎉 */
    @Serdeable
    public record ReactionBody(@Pattern(regexp = "👍|❤️|😂|😮|😢|🎉") String emoji,
                               @NotBlank String author) {
    }

    /**
     * {@code q} matches text, title, author and the comments (any case). {@code sort}: {@code active} (latest
     * comment or new post first, the default), {@code new}, {@code old} or {@code replies} (most comments first).
     */
    @Get
    public List<FeedPost> list(@Nullable @QueryValue String q, @Nullable @QueryValue String sort) {

        // ponytail: the cabin's whole forum in memory, no paging; fine for thousands of posts. Page and search in SQL if it grows.
        var needle = q == null || q.isBlank() ? null : q.trim().toLowerCase(Locale.ROOT);
        var feed = feed(posts.listOrderByCreatedAtDesc(), replies.listOrderByCreatedAt(), reactions.listOrderByCreatedAt()).stream()
            .filter(p -> needle == null || matches(p, needle));

        Comparator<FeedPost> order = switch (sort == null ? "active" : sort) {
            case "new" -> Comparator.comparing(FeedPost::createdAt).reversed();
            case "old" -> Comparator.comparing(FeedPost::createdAt);
            case "replies" -> Comparator.comparingInt((FeedPost p) -> p.comments().size()).reversed().thenComparing(FeedPost::lastActivity, Comparator.reverseOrder());
            default -> Comparator.comparing(FeedPost::lastActivity).reversed();
        };

        return feed.sorted(order).toList();
    }

    @Get("/{id}")
    public FeedPost get(Long id) {
        return feed(List.of(post(id)), replies.findByPostIdOrderByCreatedAt(id), reactions.listOrderByCreatedAt()).getFirst();
    }

    @Post
    public ForumPost create(@Body @Valid ForumPost post) {

        if (blankToNull(post.title()) == null && blankToNull(post.body()) == null) {
            throw new HttpStatusException(HttpStatus.BAD_REQUEST, "Write something");
        }
        var saved = posts.save(new ForumPost(null, blankToNull(post.title()), blankToNull(post.body()), post.author(), null, null, null));
        notifier.forumPostCreated(saved);

        return saved;
    }

    /** New title and text; who wrote it and when stay as they were. */
    @Put("/{id}")
    public ForumPost update(Long id, @Body @Valid ForumPost post) {

        var old = post(id);
        if (blankToNull(post.title()) == null && blankToNull(post.body()) == null) {
            throw new HttpStatusException(HttpStatus.BAD_REQUEST, "Write something");
        }

        return posts.update(new ForumPost(old.id(), blankToNull(post.title()), blankToNull(post.body()), old.author(), old.createdAt(), Instant.now(), old.cabinId()));
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

        var old = comment(id);

        return replies.update(new ForumReply(old.id(), old.postId(), reply.body().trim(), old.author(), old.createdAt(), Instant.now(), old.cabinId()));
    }

    @Delete("/replies/{id}")
    public void deleteReply(Long id) {
        replies.deleteById(id);
    }

    /** Your reaction to a post: a new one replaces yours, the same one again takes it away. Returns the post's reactions. */
    @Put("/{id}/reaction")
    public List<Reaction> react(Long id, @Body @Valid ReactionBody body) {

        post(id);
        toggle(reactions.findByPostIdOrderByCreatedAt(id), body, new ForumReaction(null, id, null, body.emoji(), body.author().trim(), null, null));

        return reactions.findByPostIdOrderByCreatedAt(id).stream().map(ForumController::reaction).toList();
    }

    @Put("/replies/{id}/reaction")
    public List<Reaction> reactToComment(Long id, @Body @Valid ReactionBody body) {

        comment(id);
        toggle(reactions.findByReplyIdOrderByCreatedAt(id), body, new ForumReaction(null, null, id, body.emoji(), body.author().trim(), null, null));

        return reactions.findByReplyIdOrderByCreatedAt(id).stream().map(ForumController::reaction).toList();
    }

    private void toggle(List<ForumReaction> existing, ReactionBody body, ForumReaction fresh) {

        var yours = existing.stream().filter(r -> r.author().equalsIgnoreCase(body.author().trim())).findFirst();
        if (yours.isEmpty()) {
            reactions.save(fresh);
        } else if (yours.get().emoji().equals(body.emoji())) {
            reactions.delete(yours.get());
        } else {
            var r = yours.get();
            reactions.update(new ForumReaction(r.id(), r.postId(), r.replyId(), body.emoji(), r.author(), r.createdAt(), r.cabinId()));
        }
    }

    private ForumPost post(Long id) {
        return posts.findById(id).orElseThrow(() -> new HttpStatusException(HttpStatus.NOT_FOUND, "No such post"));
    }

    private ForumReply comment(Long id) {
        return replies.findById(id).orElseThrow(() -> new HttpStatusException(HttpStatus.NOT_FOUND, "No such comment"));
    }

    private static List<FeedPost> feed(List<ForumPost> posts, List<ForumReply> replies, List<ForumReaction> reactions) {

        Map<Long, List<Reaction>> onPost = reactions.stream().filter(r -> r.postId() != null)
            .collect(Collectors.groupingBy(ForumReaction::postId, Collectors.mapping(ForumController::reaction, Collectors.toList())));
        Map<Long, List<Reaction>> onComment = reactions.stream().filter(r -> r.replyId() != null)
            .collect(Collectors.groupingBy(ForumReaction::replyId, Collectors.mapping(ForumController::reaction, Collectors.toList())));
        Function<ForumReply, Comment> comment = r -> new Comment(r.id(), r.body(), r.author(), r.createdAt(), r.editedAt(), onComment.getOrDefault(r.id(), List.of()));
        var byPost = replies.stream().collect(Collectors.groupingBy(ForumReply::postId, Collectors.mapping(comment, Collectors.toList())));

        return posts.stream().map(p -> new FeedPost(p.id(), p.title(), p.body(), p.author(), p.createdAt(), p.editedAt(),
            onPost.getOrDefault(p.id(), List.of()), byPost.getOrDefault(p.id(), List.of()))).toList();
    }

    private static Reaction reaction(ForumReaction r) {
        return new Reaction(r.emoji(), r.author());
    }

    private static boolean matches(FeedPost post, String needle) {
        return contains(post.title(), needle) || contains(post.body(), needle) || contains(post.author(), needle)
            || post.comments().stream().anyMatch(c -> contains(c.body(), needle) || contains(c.author(), needle));
    }

    private static boolean contains(@Nullable String text, String needle) {
        return text != null && text.toLowerCase(Locale.ROOT).contains(needle);
    }

    @Nullable
    private static String blankToNull(@Nullable String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }
}
