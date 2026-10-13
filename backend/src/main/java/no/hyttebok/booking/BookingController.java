package no.hyttebok.booking;

import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import jakarta.validation.Valid;

import java.util.List;

@Controller("/api/bookings")
public class BookingController {

    private final BookingRepository repository;

    public BookingController(BookingRepository repository) {
        this.repository = repository;
    }

    @Get
    public List<Booking> list() {
        return repository.listOrderByStartDate();
    }

    @Post
    public Booking create(@Body @Valid Booking booking) {
        return repository.save(booking);
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
