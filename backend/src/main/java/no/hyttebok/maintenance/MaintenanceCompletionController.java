package no.hyttebok.maintenance;

import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.validation.Valid;

import java.util.List;

/**
 * Marking a task done adds a history row and moves the task's last-completed date forward;
 * undoing removes the row and falls back to the latest remaining one.
 */
@Controller("/api/maintenance-completions")
public class MaintenanceCompletionController {

    private final MaintenanceCompletionRepository repository;
    private final MaintenanceRepository maintenance;

    public MaintenanceCompletionController(MaintenanceCompletionRepository repository, MaintenanceRepository maintenance) {
        this.repository = repository;
        this.maintenance = maintenance;
    }

    @Get
    public List<MaintenanceCompletion> list() {
        return repository.listOrderByCompletedOnDesc();
    }

    @Post
    @Transactional
    public MaintenanceCompletion create(@Body @Valid MaintenanceCompletion completion) {

        // e.g. marked done offline while someone else deleted the task: a 4xx tells the offline queue to drop it
        if (!maintenance.existsById(completion.maintenanceId())) {
            throw new HttpStatusException(HttpStatus.NOT_FOUND, "Maintenance task no longer exists");
        }

        var saved = repository.save(completion);
        maintenance.update(saved.maintenanceId(), repository.findMaxCompletedOnByMaintenanceId(saved.maintenanceId()));

        return saved;
    }

    @Delete("/{id}")
    @Transactional
    public void delete(Long id) {

        var completion = repository.findById(id).orElse(null);
        if (completion == null) {
            return;
        }

        repository.delete(completion);
        maintenance.update(completion.maintenanceId(), repository.findMaxCompletedOnByMaintenanceId(completion.maintenanceId()));
    }
}
