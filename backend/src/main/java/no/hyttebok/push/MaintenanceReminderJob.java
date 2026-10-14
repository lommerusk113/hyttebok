package no.hyttebok.push;

import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Singleton;
import no.hyttebok.maintenance.Maintenance;
import no.hyttebok.maintenance.MaintenanceRepository;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;

/**
 * Every morning: one notification listing maintenance due today, plus overdue tasks once a week
 * (on the 7th, 14th, … day overdue) so it reminds without nagging.
 */
@Singleton
public class MaintenanceReminderJob {

    private static final ZoneId OSLO = ZoneId.of("Europe/Oslo");
    private static final int REMIND_EVERY_DAYS = 7;

    private final MaintenanceRepository maintenance;
    private final Notifier notifier;

    public MaintenanceReminderJob(MaintenanceRepository maintenance, Notifier notifier) {
        this.maintenance = maintenance;
        this.notifier = notifier;
    }

    @Scheduled(cron = "0 0 8 * * *", zoneId = "Europe/Oslo")
    void run() {

        var today = LocalDate.now(OSLO);
        var dueToday = new ArrayList<Maintenance>();
        var overdue = new ArrayList<Maintenance>();
        for (var m : maintenance.listOrderByName()) {
            var days = ChronoUnit.DAYS.between(nextDue(m), today);
            if (days == 0) {
                dueToday.add(m);
            } else if (days > 0 && days % REMIND_EVERY_DAYS == 0) {
                overdue.add(m);
            }
        }
        if (dueToday.isEmpty() && overdue.isEmpty()) {
            return;
        }

        notifier.notify(Topic.MAINTENANCE, s -> {
            var en = Notifier.en(s);
            var lines = new ArrayList<String>();
            dueToday.forEach(m -> lines.add(m.name() + (en ? " – today" : " – i dag")));
            overdue.forEach(m -> lines.add(m.name() + " – " + ChronoUnit.DAYS.between(nextDue(m), today) + (en ? " days overdue" : " dager på overtid")));
            return new Notifier.Message(en ? "Maintenance" : "Vedlikehold", String.join("\n", lines), "/vedlikehold", "maintenance");
        });
    }

    /** Same rule as the app: (last completed, or the day it was added) + interval. */
    static LocalDate nextDue(Maintenance m) {

        var base = m.lastCompleted() != null ? m.lastCompleted() : LocalDate.ofInstant(m.createdAt(), OSLO);
        var n = m.intervalValue();

        return switch (m.intervalUnit()) {
            case "DAYS" -> base.plusDays(n);
            case "WEEKS" -> base.plusWeeks(n);
            case "MONTHS" -> base.plusMonths(n);
            default -> base.plusYears(n);
        };
    }
}
