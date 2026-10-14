package no.hyttebok.todo;

import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import jakarta.validation.Valid;
import no.hyttebok.push.Notifier;

import java.util.List;

@Controller("/api/todos")
public class TodoController {

    private final TodoRepository repository;
    private final Notifier notifier;

    public TodoController(TodoRepository repository, Notifier notifier) {
        this.repository = repository;
        this.notifier = notifier;
    }

    @Get
    public List<Todo> list() {
        return repository.listOrderByCreatedAtDesc();
    }

    @Post
    public Todo create(@Body @Valid Todo todo) {

        var saved = repository.save(todo);
        notifier.todoCreated(saved);

        return saved;
    }

    @Put
    public Todo update(@Body @Valid Todo todo) {
        return repository.update(todo);
    }

    @Delete("/{id}")
    public void delete(Long id) {
        repository.deleteById(id);
    }
}
