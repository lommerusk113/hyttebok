package no.hyttebok.push;

import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.security.authentication.Authentication;
import no.hyttebok.access.AccessService;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.Valid;

import java.util.Map;

/** Devices register here (identified by their push endpoint) and choose which topics they want. */
@Controller("/api/push")
public class PushController {

    private final PushSubscriptionRepository repository;
    private final WebPushSender sender;
    private final Notifier notifier;

    public PushController(PushSubscriptionRepository repository, WebPushSender sender, Notifier notifier) {
        this.repository = repository;
        this.sender = sender;
        this.notifier = notifier;
    }

    @Get("/key")
    public Map<String, String> key() {
        return Map.of("publicKey", sender.publicKey());
    }

    @Get("/subscription")
    public PushSubscription get(@QueryValue String endpoint) {
        return repository.findByEndpoint(endpoint).orElseThrow(() -> new HttpStatusException(HttpStatus.NOT_FOUND, "Not subscribed"));
    }

    /** Create or update this device's subscription. */
    @Put("/subscription")
    public PushSubscription save(@Body @Valid PushSubscription subscription, @Nullable Authentication authentication) {

        if (!WebPushSender.isPushService(subscription.endpoint())) {
            throw new HttpStatusException(HttpStatus.BAD_REQUEST, "Not a known push service");
        }

        var existing = repository.findByEndpoint(subscription.endpoint());
        var toSave = new PushSubscription(existing.map(PushSubscription::id).orElse(null), subscription.endpoint(), subscription.p256dh(),
            subscription.auth(), subscription.name(), subscription.lang(), subscription.topics(), existing.map(PushSubscription::createdAt).orElse(null),
            authentication == null ? null : AccessService.userId(authentication));

        return existing.isPresent() ? repository.update(toSave) : repository.save(toSave);
    }

    @Delete("/subscription")
    public void delete(@QueryValue String endpoint) {
        repository.deleteByEndpoint(endpoint);
    }

    @Serdeable
    public record TestRequest(String endpoint) {
    }

    @Post("/test")
    public void test(@Body TestRequest request) {

        var subscription = get(request.endpoint());
        var message = Notifier.en(subscription)
            ? new Notifier.Message("Notifications are on", "This is how messages from the cabin book will look.", "/", "test")
            : new Notifier.Message("Varsler er slått på", "Slik ser meldinger fra hytteboka ut.", "/", "test");

        notifier.deliver(subscription, message);
    }
}
