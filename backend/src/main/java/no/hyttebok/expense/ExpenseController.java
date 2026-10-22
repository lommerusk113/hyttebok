package no.hyttebok.expense;

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
        return repository.save(checked(expense));
    }

    @Put
    public Expense update(@Body @Valid Expense expense) {
        return repository.update(checked(expense));
    }

    /** Shares must add up to the amount, one per person; a settlement goes to exactly one person. */
    private static Expense checked(Expense e) {

        if ("SETTLEMENT".equals(e.kind()) && (e.splitBetween().size() != 1 || e.shares() != null)) {
            throw new HttpStatusException(HttpStatus.BAD_REQUEST, "A settlement is paid to one person");
        }
        if (e.shares() != null && (e.shares().size() != e.splitBetween().size()
            || e.shares().stream().mapToLong(Long::longValue).sum() != e.amount())) {
            throw new HttpStatusException(HttpStatus.BAD_REQUEST, "The shares must add up to the amount, one per person");
        }

        return e;
    }

    @Delete("/{id}")
    public void delete(Long id) {
        repository.deleteById(id);
    }
}
