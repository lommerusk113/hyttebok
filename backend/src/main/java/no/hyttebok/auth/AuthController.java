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
import no.hyttebok.cabin.CabinContext;
import no.hyttebok.cabin.CabinInfoRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

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
    private final boolean chatOn;

    public AuthController(LoginOffRule login, AccessService access, AppUserRepository users, PasswordResetRepository resets,
                          CabinInfoRepository cabins, @Value("${hyttebok.chat.api-key:}") String chatKey) {
        this.login = login;
        this.access = access;
        this.users = users;
        this.resets = resets;
        this.cabins = cabins;
        this.chatOn = !chatKey.isBlank();
    }

    @Serdeable
    public record SignUp(@NotBlank @Size(max = 80) String name, @NotBlank @Email @Size(max = 200) String email,
                         @NotBlank @Size(min = Passwords.MIN_LENGTH, max = Passwords.MAX_LENGTH) String password) {
    }

    @Serdeable
    public record NewPassword(@NotBlank @Size(min = Passwords.MIN_LENGTH, max = Passwords.MAX_LENGTH) String password) {
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
                     boolean adminMode, boolean chatEnabled, boolean cabinDeleted) {
    }

    @Get("/me")
    public Me me(@Nullable Authentication authentication) {

        if (authentication == null) {
            return new Me(login.loginOn(), login.googleOn(), null, null, false, false, false, !login.loginOn() && chatOn(CabinContext.current().orElse(null)), false);
        }
        var attributes = authentication.getAttributes();
        var user = new User(String.valueOf(attributes.get("name")), String.valueOf(attributes.get("email")), String.valueOf(attributes.getOrDefault("picture", "")));
        // Role and manage rights are for the cabin the app is showing (X-Hytte), if any.
        var cabin = CabinContext.current();
        var role = access.role(authentication);
        var canManage = cabin.isPresent() && access.canManage(authentication);

        return new Me(true, login.googleOn(), user, role, access.isAdmin(authentication), canManage, access.adminMode(authentication), chatOn(cabin.orElse(null)),
            cabin.flatMap(cabins::findDeleted).orElse(false));
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
        users.save(new AppUser(null, null, email, body.name().trim(), null, now, now, Passwords.hash(body.password())));
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
        users.update(user.withPasswordHash(Passwords.hash(body.password())));
        resets.update(new PasswordReset(reset.token(), reset.userId(), reset.createdBy(), reset.expiresAt(), true));
    }
}
