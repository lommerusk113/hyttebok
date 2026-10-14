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

    @Put
    public Booking update(@Body @Valid Booking booking) {
        return repository.update(booking);
    }

    @Delete("/{id}")
    public void delete(Long id) {
        repository.deleteById(id);
    }
}
