package no.hyttebok.note;

import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import jakarta.validation.Valid;

import java.util.List;

@Controller("/api/notes")
public class NoteController {

    private final NoteRepository repository;

    public NoteController(NoteRepository repository) {
        this.repository = repository;
    }

    @Get
    public List<Note> list() {
        return repository.listOrderByUpdatedAtDesc();
    }

    @Post
    public Note create(@Body @Valid Note note) {
        return repository.save(note);
    }

    @Put
    public Note update(@Body @Valid Note note) {
        return repository.update(note);
    }

    @Delete("/{id}")
    public void delete(Long id) {
        repository.deleteById(id);
    }
}
