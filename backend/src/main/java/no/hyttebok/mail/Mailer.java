package no.hyttebok.mail;

import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;
import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.util.Properties;

/**
 * Sends email over SMTP, with any provider (Brevo, Resend, Postmark, Gmail with an app password ...). Off unless
 * SMTP_HOST is set: then the app offers no email features. Port 465 is SSL, anything else STARTTLS.
 * Sent on a virtual thread, so a slow mail server never holds up a request; failures are logged.
 */
@Singleton
public class Mailer {

    private static final Logger LOG = LoggerFactory.getLogger(Mailer.class);
    private static final int SSL_PORT = 465;
    private static final String TIMEOUT_MS = "15000";

    private final String from;
    private final String fromName;
    private final Session session;

    public Mailer(@Value("${hyttebok.mail.host:}") String host,
                  @Value("${hyttebok.mail.port:587}") int port,
                  @Value("${hyttebok.mail.username:}") String username,
                  @Value("${hyttebok.mail.password:}") String password,
                  @Value("${hyttebok.mail.from:}") String from,
                  @Value("${hyttebok.mail.from-name:Hyttebok}") String fromName) {
        this.from = from.isBlank() ? username : from;
        this.fromName = fromName;
        this.session = host.isBlank() ? null : session(host, port, username, password);
    }

    public boolean enabled() {
        return session != null;
    }

    /** A plain-text email; does nothing when email is off. */
    public void send(String to, String subject, String text) {

        if (session == null) {
            return;
        }
        Thread.startVirtualThread(() -> {
            try {
                var message = new MimeMessage(session);
                message.setFrom(new InternetAddress(from, fromName, "UTF-8"));
                message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
                message.setSubject(subject, "UTF-8");
                message.setText(text, "UTF-8");
                Transport.send(message);
            } catch (MessagingException | UnsupportedEncodingException e) {
                LOG.warn("Email to {} failed: {}", to, e.toString());
            }
        });
    }

    private static Session session(String host, int port, String username, String password) {

        var props = new Properties();
        props.put("mail.smtp.host", host);
        props.put("mail.smtp.port", String.valueOf(port));
        props.put("mail.smtp.auth", String.valueOf(!username.isBlank()));
        props.put(port == SSL_PORT ? "mail.smtp.ssl.enable" : "mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.connectiontimeout", TIMEOUT_MS);
        props.put("mail.smtp.timeout", TIMEOUT_MS);
        props.put("mail.smtp.writetimeout", TIMEOUT_MS);

        return Session.getInstance(props, username.isBlank() ? null : new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(username, password);
            }
        });
    }
}
