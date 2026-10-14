package no.hyttebok.push;

import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Singleton;
import no.hyttebok.shopping.ShoppingItem;
import no.hyttebok.shopping.ShoppingItemRepository;

import java.time.Instant;
import java.util.List;

/**
 * Shopping items come in bursts ("bread, milk, coffee…"), so instead of one notification per item,
 * new items are collected and sent as one summary every few minutes.
 */
@Singleton
public class ShoppingDigestJob {

    private static final int NAMES_SHOWN = 3;

    private final ShoppingItemRepository items;
    private final Notifier notifier;
    // ponytail: in memory; items added right before a restart are simply not announced
    private Instant lastRun = Instant.now();

    public ShoppingDigestJob(ShoppingItemRepository items, Notifier notifier) {
        this.items = items;
        this.notifier = notifier;
    }

    @Scheduled(fixedDelay = "5m", initialDelay = "1m")
    void run() {

        var since = lastRun;
        lastRun = Instant.now();
        var added = items.findByCreatedAtGreaterThan(since);
        if (added.isEmpty()) {
            return;
        }

        notifier.notify(Topic.SHOPPING, s -> {
            var fromOthers = added.stream().filter(i -> !s.isFrom(i.addedBy())).map(ShoppingItem::name).toList();
            if (fromOthers.isEmpty()) {
                return null;
            }
            var en = Notifier.en(s);
            return new Notifier.Message(en ? "Shopping list updated" : "Handlelisten er oppdatert", summary(fromOthers, en), "/handleliste", "shopping");
        });
    }

    static String summary(List<String> names, boolean en) {

        var shown = String.join(", ", names.subList(0, Math.min(NAMES_SHOWN, names.size())));
        var rest = names.size() - NAMES_SHOWN;

        return rest > 0 ? shown + (en ? " and " + rest + " more" : " og " + rest + " til") : shown;
    }
}
