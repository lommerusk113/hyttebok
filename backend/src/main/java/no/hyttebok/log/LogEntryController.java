package no.hyttebok.log;

import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import jakarta.validation.Valid;

import java.util.List;

@Controller("/api/log")
public class LogEntryController {

    private final LogEntryRepository repository;

    public LogEntryController(LogEntryRepository repository) {
        this.repository = repository;
    }

    @Get
    public List<LogEntry> list() {
        return repository.listOrderByCreatedAtDesc();
    }

    @Post
    public LogEntry create(@Body @Valid LogEntry logEntry) {
        return repository.save(logEntry);
    }

    @Put
    public LogEntry update(@Body @Valid LogEntry logEntry) {
        return repository.update(logEntry);
    }

    @Delete("/{id}")
    public void delete(Long id) {
        repository.deleteById(id);
    }
}
