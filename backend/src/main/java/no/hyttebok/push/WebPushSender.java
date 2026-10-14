package no.hyttebok.push;

import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

/**
 * Delivers encrypted messages to browser push services. The VAPID key pair is created on first start
 * and kept in the database, so there is nothing to configure.
 */
@Singleton
public class WebPushSender {

    private static final long VAPID_ID = 1L;
    // Only real push services; otherwise anyone could make this server send requests to any address.
    private static final List<String> PUSH_HOSTS = List.of("fcm.googleapis.com", ".push.apple.com", ".push.services.mozilla.com", ".notify.windows.com");

    private final String subject;
    private final String publicKey;
    private final PrivateKey privateKey;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public WebPushSender(VapidKeyRepository keys, @Value("${hyttebok.push.subject}") String subject) throws GeneralSecurityException {

        this.subject = subject;
        var stored = keys.findById(VAPID_ID).orElseGet(() -> {
            var pair = WebPushCrypto.newKeyPair();
            return keys.save(new VapidKey(VAPID_ID,
                WebPushCrypto.B64.encodeToString(WebPushCrypto.rawPublicKey((ECPublicKey) pair.getPublic())),
                Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded())));
        });

        this.publicKey = stored.publicKey();
        this.privateKey = KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(stored.privateKey())));
    }

    public String publicKey() {
        return publicKey;
    }

    static boolean isPushService(String endpoint) {

        var uri = URI.create(endpoint);
        var host = uri.getHost() == null ? "" : uri.getHost();

        return "https".equals(uri.getScheme()) && PUSH_HOSTS.stream().anyMatch(h -> h.startsWith(".") ? host.endsWith(h) : host.equals(h));
    }

    /** Returns the push service's HTTP status; 404/410 mean the subscription is gone. */
    public int send(PushSubscription subscription, String json) throws GeneralSecurityException, IOException, InterruptedException {

        var body = WebPushCrypto.encrypt(json.getBytes(StandardCharsets.UTF_8),
            WebPushCrypto.B64_DECODE.decode(subscription.p256dh()), WebPushCrypto.B64_DECODE.decode(subscription.auth()));
        var uri = URI.create(subscription.endpoint());
        var audience = uri.getScheme() + "://" + uri.getAuthority();
        var jwt = WebPushCrypto.vapidJwt(audience, subject, Instant.now().plus(Duration.ofHours(12)).getEpochSecond(), privateKey);

        var request = HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(15))
            .header("TTL", "86400")
            .header("Urgency", "normal")
            .header("Content-Encoding", "aes128gcm")
            .header("Content-Type", "application/octet-stream")
            .header("Authorization", "vapid t=" + jwt + ", k=" + publicKey)
            .POST(HttpRequest.BodyPublishers.ofByteArray(body))
            .build();

        return http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
    }
}
