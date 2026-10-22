package no.hyttebok.expense;

import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import jakarta.validation.Valid;

import java.util.List;

/** "Utgifter": shared costs and paying each other back. Balances are worked out in the app from the full list. */
@Controller("/api/expenses")
public class ExpenseController {

    private final ExpenseRepository repository;

    public ExpenseController(ExpenseRepository repository) {
        this.repository = repository;
    }

    @Get
    public List<Expense> list() {
        return repository.listOrderBySpentOnDesc();
    }

    @Post
    public Expense create(@Body @Valid Expense expense) {
        return repository.save(expense);
    }

    @Put
    public Expense update(@Body @Valid Expense expense) {
        return repository.update(expense);
    }

    @Delete("/{id}")
    public void delete(Long id) {
        repository.deleteById(id);
    }
}
