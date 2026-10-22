package no.hyttebok.push;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.json.JsonMapper;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.inject.Singleton;
import no.hyttebok.auth.LoginOffRule;
import no.hyttebok.booking.Booking;
import no.hyttebok.cabin.CabinContext;
import no.hyttebok.forum.ForumPost;
import no.hyttebok.forum.ForumReply;
import no.hyttebok.log.LogEntry;
import no.hyttebok.suggestion.Suggestion;
import no.hyttebok.todo.Todo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

/**
 * Turns app events into notifications for the devices that asked for them, in each device's language.
 * Sending happens on a virtual thread so saving something never waits for push services.
 */
@Singleton
public class Notifier {

    private static final Logger LOG = LoggerFactory.getLogger(Notifier.class);
    private static final Locale NB = Locale.of("nb", "NO");

    private final PushSubscriptionRepository subscriptions;
    private final WebPushSender sender;
    private final JsonMapper json;
    private final LoginOffRule login;

    public Notifier(PushSubscriptionRepository subscriptions, WebPushSender sender, JsonMapper json, LoginOffRule login) {
        this.subscriptions = subscriptions;
        this.sender = sender;
        this.json = json;
        this.login = login;
    }

    @Serdeable
    public record Message(String title, String body, String url, String tag) {
    }

    /**
     * Tells the devices following the current cabin (the request's, or the job's) that asked for {@code topic}.
     * {@code messageFor} returns null to skip a device (e.g. nothing relevant for that person).
     */
    public void notify(Topic topic, Function<PushSubscription, Message> messageFor) {
        var cabinId = CabinContext.require(); // read now: the sending thread below is no longer in the request
        Thread.startVirtualThread(() -> {
            // Without login there are no members: every device that follows the cabin.
            var devices = login.loginOn() ? subscriptions.findMembersByCabinId(cabinId) : subscriptions.findByCabinId(cabinId);
            for (var subscription : devices) {
                if (!subscription.wants(topic)) {
                    continue;
                }
                var message = messageFor.apply(subscription);
                if (message != null) {
                    deliver(subscription, inCabin(message, cabinId));
                }
            }
        });
    }

    /** Links open the right cabin ("/booking?id=3" → "/h/2/booking?id=3"); tags keep cabins apart on the phone. */
    static Message inCabin(Message message, long cabinId) {
        return new Message(message.title(), message.body(), "/h/" + cabinId + message.url(), "c" + cabinId + "-" + message.tag());
    }

    public void deliver(PushSubscription subscription, Message message) {
        try {
            var status = sender.send(subscription, json.writeValueAsString(message));
            if (status == 404 || status == 410) {
                subscriptions.delete(subscription); // the device unsubscribed or the app was removed
            } else if (status >= 400) {
                LOG.warn("Push to {} failed with HTTP {}", subscription.endpoint(), status);
            }
        } catch (Exception e) {
            LOG.warn("Push to {} failed: {}", subscription.endpoint(), e.toString());
        }
    }

    // ---- events (actors are left out: you don't get notified about your own changes) ----

    public void bookingCreated(Booking booking) {
        notify(Topic.BOOKING, s -> s.isFrom(booking.guestName()) ? null : en(s)
            ? new Message("New booking", booking.guestName() + " booked " + period(booking, Locale.ENGLISH), "/booking?id=" + booking.id(), "booking-" + booking.id())
            : new Message("Ny booking", booking.guestName() + " har booket " + period(booking, NB), "/booking?id=" + booking.id(), "booking-" + booking.id()));
    }

    public void postCreated(LogEntry entry) {
        notify(Topic.POST, s -> s.isFrom(entry.author()) ? null : new Message(
            en(s) ? "New in the cabin book" : "Nytt i hytteboka", entry.author() + ": " + entry.title(), "/hyttebok?id=" + entry.id(), "post-" + entry.id()));
    }

    public void suggestionCreated(Suggestion suggestion) {
        notify(Topic.SUGGESTION, s -> s.isFrom(suggestion.author()) ? null : new Message(
            en(s) ? "New idea" : "Nytt forslag", by(suggestion.author()) + suggestion.title(), "BUY".equals(suggestion.category()) ? "/handleliste" : "/vedlikehold/forslag", "suggestion-" + suggestion.id()));
    }

    public void todoCreated(Todo todo) {
        notify(Topic.TODO, s -> s.isFrom(todo.createdBy()) ? null : new Message(
            en(s) ? "New task" : "Ny oppgave", by(todo.createdBy()) + todo.title(), "/vedlikehold?todo=" + todo.id(), "todo-" + todo.id()));
    }

    public void forumPostCreated(ForumPost post) {
        notify(Topic.FORUM, s -> s.isFrom(post.author()) ? null : new Message(
            en(s) ? "New in the forum" : "Nytt i forumet", by(post.author()) + label(post), "/forum/" + post.id(), "forum-" + post.id()));
    }

    /** Only to those in the thread (who wrote the post or replied before), so a busy forum doesn't buzz everyone. */
    public void forumReplied(ForumPost post, ForumReply reply, List<String> earlier) {
        notify(Topic.FORUM, s -> s.isFrom(reply.author()) || (!s.isFrom(post.author()) && earlier.stream().noneMatch(s::isFrom)) ? null : new Message(
            (en(s) ? "Comment: " : "Kommentar: ") + label(post), by(reply.author()) + (reply.body() == null ? "📷" : shorten(reply.body())), "/forum/" + post.id(), "forum-" + post.id()));
    }

    /** To the others in the cabin: someone arrived. */
    public void checkedIn(Booking booking) {
        notify(Topic.STAY, s -> s.isFrom(booking.checkedInBy()) ? null : new Message(
            en(s) ? booking.guestName() + " is at the cabin" : booking.guestName() + " er på hytta",
            en(s) ? "Checked in · until " + day(booking.endDate(), Locale.ENGLISH) : "Sjekket inn · til " + day(booking.endDate(), NB), "/", "stay-" + booking.id()));
    }

    /** To the others: the cabin is closed up (or someone left, if others are still there). */
    public void checkedOut(Booking booking, @Nullable String by) {
        var who = by == null ? booking.guestName() : by;
        notify(Topic.STAY, s -> s.isFrom(who) ? null : new Message(
            en(s) ? who + " has checked out" : who + " har sjekket ut", en(s) ? "The cabin is closed up." : "Hytta er stengt.", "/", "stay-" + booking.id()));
    }

    /** The evening before: to whoever the booking is for. */
    public void arrivingTomorrow(Booking booking, String cabinName, int toBuy) {
        notify(Topic.STAY, s -> !s.isFrom(booking.guestName()) ? null : new Message(
            en(s) ? "Tomorrow: " + cabinName : "I morgen: " + cabinName,
            en(s) ? "Have a good trip! " + (toBuy > 0 ? toBuy + " things on the shopping list." : "Remember to check in when you arrive.")
                : "God tur! " + (toBuy > 0 ? toBuy + " ting på handlelista." : "Husk å sjekke inn når du kommer fram."),
            "/handleliste", "arriving-" + booking.id()));
    }

    /** On the last day, to whoever is still checked in. */
    public void stillCheckedIn(Booking booking) {
        notify(Topic.STAY, s -> !(s.isFrom(booking.checkedInBy()) || s.isFrom(booking.guestName())) ? null : new Message(
            en(s) ? "Leaving today?" : "Drar du i dag?", en(s) ? "Remember to check out: go through the departure list." : "Husk å sjekke ut: gå gjennom avreiselista.",
            "/", "leaving-" + booking.id()));
    }

    /** To the cabin's owners: someone joined with an invitation link. */
    public void memberJoined(String name, Set<Long> owners) {
        notify(Topic.MEMBERS, s -> !owners.contains(s.userId()) ? null : new Message(
            en(s) ? "New member" : "Nytt medlem", en(s) ? name + " joined the cabin." : name + " ble med i hytta.", "/tilgang", "member"));
    }

    /** To the cabin's owners: the AI chat has used {@code percent} (80 or 100) of this month's budget. */
    public void chatBudget(int percent, Set<Long> owners) {
        notify(Topic.MEMBERS, s -> !owners.contains(s.userId()) ? null : new Message(
            en(s) ? "AI chat: " + percent + "% used" : "AI-chatten: " + percent + " % brukt",
            percent >= 100 ? (en(s) ? "This month's allowance is used up; the chat works again on the 1st." : "Månedens kvote er brukt opp; chatten virker igjen den 1.")
                : (en(s) ? "Most of this month's allowance is used. See who has used it under Access." : "Det meste av månedens kvote er brukt. Se hvem som har brukt den under Tilgang."),
            "/tilgang", "chat-budget"));
    }

    private static String day(LocalDate date, Locale locale) {
        return date.format(DateTimeFormatter.ofPattern(locale == NB ? "EEE d. MMM" : "EEE d MMM", locale));
    }

    static boolean en(PushSubscription subscription) {
        return "en".equals(subscription.lang());
    }

    /** What a forum post is called in a notification: its title, the start of its text, or 📷 for photos only. */
    private static String label(ForumPost post) {
        return post.title() != null ? post.title() : post.body() == null ? "📷" : shorten(post.body().replace('\n', ' '), 60);
    }

    /** Push messages have a size limit (about 4 kB), and a phone shows a couple of lines anyway. */
    private static String shorten(String text) {
        return shorten(text, 140);
    }

    private static String shorten(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }

    private static String by(@Nullable String person) {
        return person == null || person.isBlank() ? "" : person + ": ";
    }

    /** "20.–22. nov." / "20–22 Nov" within a month, otherwise both dates in full. */
    static String period(Booking booking, Locale locale) {

        var nb = locale == NB;
        var full = DateTimeFormatter.ofPattern(nb ? "d. MMM" : "d MMM", locale);
        LocalDate start = booking.startDate();
        LocalDate end = booking.endDate();
        if (start.equals(end)) {
            return start.format(full);
        }
        if (start.getMonth() == end.getMonth() && start.getYear() == end.getYear()) {
            return start.getDayOfMonth() + (nb ? ".–" : "–") + end.format(full);
        }

        return start.format(full) + "–" + end.format(full);
    }
}
