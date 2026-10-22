package no.hyttebok.booking;

import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import jakarta.validation.Valid;
import no.hyttebok.push.Notifier;

import java.util.List;

@Controller("/api/bookings")
public class BookingController {

    private final BookingRepository repository;
    private final Notifier notifier;

    public BookingController(BookingRepository repository, Notifier notifier) {
        this.repository = repository;
        this.notifier = notifier;
    }

    @Get
    public List<Booking> list() {
        return repository.listOrderByStartDate();
    }

    @Post
    public Booking create(@Body @Valid Booking booking) {

        var saved = repository.save(booking);
        notifier.bookingCreated(saved);

        return saved;
    }

    /** Checking in or out (the check-in fields going from empty to set) tells the others. */
    @Put
    public Booking update(@Body @Valid Booking booking) {

        var before = booking.id() == null ? null : repository.findById(booking.id()).orElse(null);
        var saved = repository.update(booking);
        if (before != null && before.checkedInAt() == null && saved.checkedInAt() != null) {
            notifier.checkedIn(saved);
        }
        if (before != null && before.checkedOutAt() == null && saved.checkedOutAt() != null) {
            notifier.checkedOut(saved, saved.checkedInBy());
        }

        return saved;
    }

    @Delete("/{id}")
    public void delete(Long id) {
        repository.deleteById(id);
    }
}
