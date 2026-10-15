package no.hyttebok.push;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.json.JsonMapper;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.inject.Singleton;
import no.hyttebok.access.AccessService;
import no.hyttebok.access.Membership;
import no.hyttebok.access.MembershipRepository;
import no.hyttebok.auth.AppUser;
import no.hyttebok.auth.AppUserRepository;
import no.hyttebok.booking.Booking;
import no.hyttebok.log.LogEntry;
import no.hyttebok.suggestion.Suggestion;
import no.hyttebok.todo.Todo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
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
    private final MembershipRepository memberships;
    private final AppUserRepository users;
    private final AccessService access;

    public Notifier(PushSubscriptionRepository subscriptions, WebPushSender sender, JsonMapper json,
                    MembershipRepository memberships, AppUserRepository users, AccessService access) {
        this.subscriptions = subscriptions;
        this.sender = sender;
        this.json = json;
        this.memberships = memberships;
        this.users = users;
        this.access = access;
    }

    @Serdeable
    public record Message(String title, String body, String url, String tag) {
    }

    /** {@code messageFor} returns null to skip a device (e.g. nothing relevant for that person). */
    public void notify(Topic topic, Function<PushSubscription, Message> messageFor) {
        Thread.startVirtualThread(() -> {
            for (var subscription : subscriptions.findAll()) {
                if (!subscription.wants(topic)) {
                    continue;
                }
                var message = messageFor.apply(subscription);
                if (message != null) {
                    deliver(subscription, message);
                }
            }
        });
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

    /**
     * Someone asked for access: tell the devices of everyone who can approve it (owners and system admins).
     * Not tied to a topic: it's a question only they can answer, and they'd otherwise never see it.
     */
    public void accessRequested(Long requestId, Long requesterId, String name, @Nullable String message) {
        Thread.startVirtualThread(() -> {
            for (var subscription : subscriptions.findAll()) {
                if (subscription.userId() == null || subscription.userId().equals(requesterId) || !canApprove(subscription.userId())) {
                    continue;
                }
                var quote = message == null ? "" : ": «" + message + "»";
                deliver(subscription, en(subscription)
                    ? new Message("Access request", name + " is asking for access" + quote, "/tilgang", "access-" + requestId)
                    : new Message("Forespørsel om tilgang", name + " ber om tilgang" + quote, "/tilgang", "access-" + requestId));
            }
        });
    }

    private boolean canApprove(Long userId) {
        return memberships.findById(userId).map(m -> Membership.OWNER.equals(m.role())).orElse(false)
            || users.findById(userId).filter(AppUser::emailVerified).map(u -> access.isAdmin(u.email())).orElse(false);
    }

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

    static boolean en(PushSubscription subscription) {
        return "en".equals(subscription.lang());
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
