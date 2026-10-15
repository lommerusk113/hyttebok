package no.hyttebok.wiki;

import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.exceptions.HttpStatusException;
import jakarta.validation.Valid;

import java.util.List;

/** The cabin's wiki: pages anyone in the cabin can write and edit. The chatbot reads them too. */
@Controller("/api/wiki")
public class WikiController {

    private final WikiPageRepository repository;

    public WikiController(WikiPageRepository repository) {
        this.repository = repository;
    }

    @Get
    public List<WikiPage> list() {
        return repository.listOrderByTitle();
    }

    @Post
    public WikiPage create(@Body @Valid WikiPage page) {
        return repository.save(new WikiPage(null, page.title().trim(), page.content(), page.updatedBy(), null, null));
    }

    @Put
    public WikiPage update(@Body @Valid WikiPage page) {

        var old = repository.findById(page.id()).orElseThrow(() -> new HttpStatusException(HttpStatus.NOT_FOUND, "No such page"));

        return repository.update(new WikiPage(old.id(), page.title().trim(), page.content(), page.updatedBy(), null, old.cabinId()));
    }

    @Delete("/{id}")
    public void delete(Long id) {
        repository.deleteById(id);
    }
}
