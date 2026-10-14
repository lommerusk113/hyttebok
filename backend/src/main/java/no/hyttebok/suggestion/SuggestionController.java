package no.hyttebok.suggestion;

import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import jakarta.validation.Valid;
import no.hyttebok.push.Notifier;

import java.util.List;

@Controller("/api/suggestions")
public class SuggestionController {

    private final SuggestionRepository repository;
    private final Notifier notifier;

    public SuggestionController(SuggestionRepository repository, Notifier notifier) {
        this.repository = repository;
        this.notifier = notifier;
    }

    @Get
    public List<Suggestion> list() {
        return repository.listOrderByCreatedAtDesc();
    }

    @Post
    public Suggestion create(@Body @Valid Suggestion suggestion) {

        var saved = repository.save(suggestion);
        notifier.suggestionCreated(saved);

        return saved;
    }

    @Put
    public Suggestion update(@Body @Valid Suggestion suggestion) {
        return repository.update(suggestion);
    }

    @Delete("/{id}")
    public void delete(Long id) {
        repository.deleteById(id);
    }
}
