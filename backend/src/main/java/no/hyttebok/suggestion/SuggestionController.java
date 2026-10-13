package no.hyttebok.suggestion;

import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import jakarta.validation.Valid;

import java.util.List;

@Controller("/api/suggestions")
public class SuggestionController {

    private final SuggestionRepository repository;

    public SuggestionController(SuggestionRepository repository) {
        this.repository = repository;
    }

    @Get
    public List<Suggestion> list() {
        return repository.listOrderByCreatedAtDesc();
    }

    @Post
    public Suggestion create(@Body @Valid Suggestion suggestion) {
        return repository.save(suggestion);
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
