package no.hyttebok.maintenance;

import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.validation.Valid;

import java.util.List;

@Controller("/api/maintenance")
public class MaintenanceController {

    private final MaintenanceRepository repository;
    private final MaintenanceCompletionRepository completions;

    public MaintenanceController(MaintenanceRepository repository, MaintenanceCompletionRepository completions) {
        this.repository = repository;
        this.completions = completions;
    }

    @Get
    public List<Maintenance> list() {
        return repository.listOrderByName();
    }

    /**
     * A "last completed" date given on creation also goes into the history, so it shows in the year overview.
     */
    @Post
    @Transactional
    public Maintenance create(@Body @Valid Maintenance maintenance) {

        var saved = repository.save(maintenance);
        if (saved.lastCompleted() != null) {
            completions.save(new MaintenanceCompletion(null, saved.id(), saved.lastCompleted(), null, null));
        }

        return saved;
    }

    /**
     * "Last completed" always follows the history, so an edit from a phone with stale data can't undo
     * someone else's completion. A later date typed into the form is recorded as a completion.
     */
    @Put
    @Transactional
    public Maintenance update(@Body @Valid Maintenance maintenance) {

        var latest = completions.findMaxCompletedOnByMaintenanceId(maintenance.id());
        var typed = maintenance.lastCompleted();
        if (typed != null && (latest == null || typed.isAfter(latest))) {
            completions.save(new MaintenanceCompletion(null, maintenance.id(), typed, null, null));
            latest = typed;
        }

        var updated = new Maintenance(maintenance.id(), maintenance.name(), maintenance.description(), maintenance.intervalValue(),
            maintenance.intervalUnit(), latest, maintenance.createdAt());

        return repository.update(updated);
    }

    @Delete("/{id}")
    public void delete(Long id) {
        repository.deleteById(id);
    }
}
