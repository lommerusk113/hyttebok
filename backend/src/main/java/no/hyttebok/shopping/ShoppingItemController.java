package no.hyttebok.shopping;

import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import jakarta.validation.Valid;

import java.util.List;

@Controller("/api/shopping")
public class ShoppingItemController {

    private final ShoppingItemRepository repository;

    public ShoppingItemController(ShoppingItemRepository repository) {
        this.repository = repository;
    }

    @Get
    public List<ShoppingItem> list() {
        return repository.listOrderByCreatedAtDesc();
    }

    @Post
    public ShoppingItem create(@Body @Valid ShoppingItem shoppingItem) {
        return repository.save(shoppingItem);
    }

    @Put
    public ShoppingItem update(@Body @Valid ShoppingItem shoppingItem) {
        return repository.update(shoppingItem);
    }

    @Delete("/{id}")
    public void delete(Long id) {
        repository.deleteById(id);
    }
}
