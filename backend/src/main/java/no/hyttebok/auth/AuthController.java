package no.hyttebok.auth;

import io.micronaut.context.annotation.Value;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Status;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.rules.SecurityRule;
import io.micronaut.serde.annotation.Serdeable;
import no.hyttebok.access.AccessService;
import no.hyttebok.audit.Audit;
import no.hyttebok.cabin.CabinContext;
import no.hyttebok.cabin.CabinInfoRepository;
import no.hyttebok.mail.Links;
import no.hyttebok.mail.Mailer;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tells the app whether login is on, who is logged in (null user = show the login page) and what they may do:
 * no role and not admin = show the "no access yet" page.
 */
@Controller("/api/auth")
@Secured(SecurityRule.IS_ANONYMOUS)
public class AuthController {

    private final LoginOffRule login;
    private final AccessService access;
    private final AppUserRepository users;
    private final PasswordResetRepository resets;
    private final CabinInfoRepository cabins;
    private static final int FORGOT_EVERY_MINUTES = 2;
    private static final Duration FORGOT_EVERY = Duration.ofMinutes(FORGOT_EVERY_MINUTES);

    private final Profiles profiles;
    private final Audit audit;
    private final Mailer mailer;
    private final Links links;
    private final boolean chatOn;
    // ponytail: in memory, a restart forgets it; fine for one server.
    private final Map<String, Instant> forgotSent = new ConcurrentHashMap<>();

    public AuthController(LoginOffRule login, AccessService access, AppUserRepository users, PasswordResetRepository resets,
                          CabinInfoRepository cabins, Profiles profiles, Audit audit, Mailer mailer, Links links,
                          @Value("${hyttebok.chat.api-key:}") String chatKey) {
        this.login = login;
        this.access = access;
        this.users = users;
        this.resets = resets;
        this.cabins = cabins;
        this.profiles = profiles;
        this.audit = audit;
        this.mailer = mailer;
        this.links = links;
        this.chatOn = !chatKey.isBlank();
    }

    @Serdeable
    public record SignUp(@NotBlank @Size(max = 80) String name, @NotBlank @Email @Size(max = 200) String email,
                         @NotBlank @Size(min = Passwords.MIN_LENGTH, max = Passwords.MAX_LENGTH) String password) {
    }

    @Serdeable
    public record NewPassword(@NotBlank @Size(min = Passwords.MIN_LENGTH, max = Passwords.MAX_LENGTH) String password) {
    }

    /** {@code lang}: the email's language ("en" or Norwegian). */
    @Serdeable
    public record Forgot(@NotBlank @Email @Size(max = 200) String email, @Nullable String lang) {
    }

    @Serdeable
    public record Recover(@NotBlank @Size(max = 200) String email, @NotBlank @Size(max = 40) String code) {
    }

    @Serdeable
    public record ResetInfo(boolean valid, @Nullable String email) {
    }

    @Serdeable
    public record User(String name, String email, String picture) {
    }

    /**
     * For the cabin the app is showing: {@code role} (OWNER, MEMBER or null = no access), {@code canManage} (owner,
     * or admin in admin mode: the "Tilgang" tab). {@code admin}: may switch on admin mode.
     * {@code cabinDeleted}: the cabin was deleted (no access for its members; an admin can bring it back).
     */
    /** {@code googleEnabled}: show "Logg inn med Google" (GOOGLE_CLIENT_ID is set); email + password always works. */
    @Serdeable
    public record Me(boolean loginEnabled, boolean googleEnabled, @Nullable User user, @Nullable String role, boolean admin, boolean canManage,
                     boolean adminMode, boolean chatEnabled, boolean cabinDeleted, boolean emailEnabled) {
    }

    @Get("/me")
    public Me me(@Nullable Authentication authentication) {

        if (authentication == null) {
            return new Me(login.loginOn(), login.googleOn(), null, null, false, false, false, !login.loginOn() && chatOn(CabinContext.current().orElse(null)), false,
                mailer.enabled());
        }
        var attributes = authentication.getAttributes();
        var user = new User(String.valueOf(attributes.get("name")), String.valueOf(attributes.get("email")), String.valueOf(attributes.getOrDefault("picture", "")));
        // Role and manage rights are for the cabin the app is showing (X-Hytte), if any.
        var cabin = CabinContext.current();
        var role = access.role(authentication);
        var canManage = cabin.isPresent() && access.canManage(authentication);

        return new Me(true, login.googleOn(), user, role, access.isAdmin(authentication), canManage, access.adminMode(authentication), chatOn(cabin.orElse(null)),
            cabin.flatMap(cabins::findDeleted).orElse(false), mailer.enabled());
    }

    /** The AI chat is shown in a cabin when the server has a key and a system admin turned it on there. */
    private boolean chatOn(@Nullable Long cabinId) {
        return chatOn && cabinId != null && cabins.findChatEnabled(cabinId).orElse(false);
    }

    /** New email + password account. It starts without access, like a Google login; the app then logs in via POST /login. */
    @Post("/signup")
    @Status(HttpStatus.CREATED)
    public void signUp(@Body @Valid SignUp body) {

        if (!login.loginOn()) {
            throw new HttpStatusException(HttpStatus.NOT_FOUND, "Login is switched off");
        }
        var email = body.email().trim();
        if (users.findByEmail(email).isPresent()) {
            throw new HttpStatusException(HttpStatus.CONFLICT, "An account with this email already exists");
        }
        var now = Instant.now();
        users.save(new AppUser(null, null, email, body.name().trim(), null, now, now, Passwords.hash(body.password()), 0));
    }

    @Get("/password-reset/{token}")
    public ResetInfo resetInfo(String token) {
        return resets.findById(token).filter(r -> r.usable(Instant.now()))
            .flatMap(r -> users.findById(r.userId())).map(u -> new ResetInfo(true, u.email())).orElse(new ResetInfo(false, null));
    }

    @Post("/password-reset/{token}")
    public void resetPassword(String token, @Body @Valid NewPassword body) {

        var reset = resets.findById(token).filter(r -> r.usable(Instant.now()))
            .orElseThrow(() -> new HttpStatusException(HttpStatus.GONE, "This link has expired or was already used"));
        var user = users.findById(reset.userId()).orElseThrow(() -> new HttpStatusException(HttpStatus.GONE, "No such user"));
        users.update(user.withNewPassword(Passwords.hash(body.password())));
        resets.update(new PasswordReset(reset.token(), reset.userId(), reset.createdBy(), reset.expiresAt(), true));
        audit.log(null, null, Audit.PASSWORD_RESET, user.id(), null);
    }

    /**
     * Forgot the password: a one-time link by email (valid a day). Always 202, account or not, so nobody can find
     * out which emails have an account; at most one email per address every {@value #FORGOT_EVERY_MINUTES} minutes.
     */
    @Post("/forgot")
    @Status(HttpStatus.ACCEPTED)
    public void forgot(@Body @Valid Forgot body) {

        if (!mailer.enabled()) {
            throw new HttpStatusException(HttpStatus.NOT_FOUND, "Email is not set up");
        }
        var email = body.email().trim().toLowerCase(Locale.ROOT);
        var now = Instant.now();
        var last = forgotSent.get(email);
        if (last != null && last.plus(FORGOT_EVERY).isAfter(now)) {
            return;
        }
        forgotSent.put(email, now);

        users.findByEmail(email).ifPresent(user -> {
            var link = links.to("/nytt-passord/" + profiles.resetLink(user.id(), null).token());
            var en = "en".equals(body.lang());
            mailer.send(user.email(), en ? "New password for Hyttebok" : "Nytt passord til Hyttebok", en
                ? "Hi " + displayName(user) + ",\n\nSomeone (hopefully you) asked for a new password for Hyttebok. Open this link to choose one:\n\n" + link
                    + "\n\nThe link works once, for 24 hours. If you didn't ask, you can ignore this email: nothing has changed.\n"
                : "Hei " + displayName(user) + ",\n\nNoen (forhåpentligvis du) har bedt om nytt passord til Hyttebok. Åpne lenken for å velge et nytt:\n\n" + link
                    + "\n\nLenken virker én gang, i 24 timer. Har du ikke bedt om dette, kan du se bort fra e-posten: ingenting er endret.\n");
            audit.log(null, null, Audit.RESET_EMAIL, user.id(), null);
        });
    }

    private static String displayName(AppUser user) {
        return user.name() == null ? user.email() : user.name();
    }

    /** Forgot the password: a recovery code (saved earlier on "Min profil") gives a one-time new-password link. */
    @Post("/recover")
    public Profiles.ResetLink recover(@Body @Valid Recover body) {

        var link = profiles.redeem(body.email(), body.code())
            .orElseThrow(() -> new HttpStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Wrong email or code"));
        resets.findById(link.token()).ifPresent(r -> audit.log(null, null, Audit.RECOVERY_USED, r.userId(), null));

        return link;
    }
}
