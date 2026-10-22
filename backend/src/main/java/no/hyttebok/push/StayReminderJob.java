package no.hyttebok.push;

import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Singleton;
import no.hyttebok.booking.BookingRepository;
import no.hyttebok.cabin.CabinContext;
import no.hyttebok.cabin.CabinInfoRepository;
import no.hyttebok.shopping.ShoppingItemRepository;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.function.Consumer;

/**
 * Reminders about your own stay: the evening before you arrive (with what's on the shopping list), and at noon on
 * the last day if you're still checked in. Bookings are names, so they go to the devices of that name.
 */
@Singleton
public class StayReminderJob {

    private static final ZoneId OSLO = ZoneId.of("Europe/Oslo");

    private final CabinInfoRepository cabins;
    private final BookingRepository bookings;
    private final ShoppingItemRepository shopping;
    private final Notifier notifier;

    public StayReminderJob(CabinInfoRepository cabins, BookingRepository bookings, ShoppingItemRepository shopping, Notifier notifier) {
        this.cabins = cabins;
        this.bookings = bookings;
        this.shopping = shopping;
        this.notifier = notifier;
    }

    @Scheduled(cron = "0 0 18 * * *", zoneId = "Europe/Oslo")
    void eveningBefore() {
        forEachCabin(name -> {
            var tomorrow = LocalDate.now(OSLO).plusDays(1);
            var toBuy = (int) shopping.listOrderByCreatedAtDesc().stream().filter(i -> !i.bought()).count();
            bookings.listOrderByStartDate().stream().filter(b -> b.startDate().equals(tomorrow) && b.checkedInAt() == null)
                .forEach(b -> notifier.arrivingTomorrow(b, name, toBuy));
        });
    }

    @Scheduled(cron = "0 0 12 * * *", zoneId = "Europe/Oslo")
    void lastDay() {
        forEachCabin(name -> {
            var today = LocalDate.now(OSLO);
            bookings.listOrderByStartDate().stream().filter(b -> b.endDate().equals(today) && b.checkedInAt() != null && b.checkedOutAt() == null)
                .forEach(notifier::stillCheckedIn);
        });
    }

    /** Runs {@code work} (given the cabin's name) in every cabin that isn't deleted. */
    private void forEachCabin(Consumer<String> work) {

        var deleted = cabins.findDeletedIds();
        for (var cabin : cabins.findAll()) {
            if (!deleted.contains(cabin.id())) {
                CabinContext.runAs(cabin.id(), () -> work.accept(cabin.name()));
            }
        }
    }
}
