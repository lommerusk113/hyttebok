package no.hyttebok.auth;

import io.micronaut.context.annotation.Factory;
import io.micronaut.security.token.jwt.signature.SignatureGeneratorConfiguration;
import io.micronaut.security.token.jwt.signature.secret.SecretSignature;
import io.micronaut.security.token.jwt.signature.secret.SecretSignatureConfiguration;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * Signs the login cookie. The key is generated on first start and kept in the database, so nothing has to be
 * configured and nobody is logged out when the server restarts.
 */
@Factory
public class SessionSigningFactory {

    private static final String NAME = "session-signing";
    private static final int KEY_BYTES = 32;

    @Singleton
    @Named("generator")
    SignatureGeneratorConfiguration generator(AppSecretRepository secrets) {

        var secret = secrets.findById(NAME).orElseGet(() -> {
            var bytes = new byte[KEY_BYTES];
            new SecureRandom().nextBytes(bytes);
            return secrets.save(new AppSecret(NAME, HexFormat.of().formatHex(bytes)));
        });
        var configuration = new SecretSignatureConfiguration("generator");
        configuration.setSecret(secret.value());

        return new SecretSignature(configuration);
    }
}
