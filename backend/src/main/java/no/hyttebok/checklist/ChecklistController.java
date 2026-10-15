package no.hyttebok.checklist;

import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import jakarta.validation.Valid;

import java.util.List;

@Controller("/api/checklist")
public class ChecklistController {

    private final ChecklistItemRepository repository;

    public ChecklistController(ChecklistItemRepository repository) {
        this.repository = repository;
    }

    @Get
    public List<ChecklistItem> list() {
        return repository.listOrderByName();
    }

    @Post
    public ChecklistItem create(@Body @Valid ChecklistItem item) {
        return repository.save(new ChecklistItem(null, item.name().trim(), null, null));
    }

    @Delete("/{id}")
    public void delete(Long id) {
        repository.deleteById(id);
    }
}
