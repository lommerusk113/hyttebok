package no.hyttebok.access;

import io.micronaut.context.annotation.Value;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.context.ServerRequestContext;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.security.authentication.Authentication;
import jakarta.inject.Singleton;
import no.hyttebok.cabin.CabinContext;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Who may do what, per cabin. Owners invite people and approve requests; members use the cabin.
 * System admins (ADMIN_EMAILS, Google-verified) are ordinary users until they switch on admin mode
 * (the app sends "X-Admin-Mode: on"); then they see every cabin and act as owner in any of them, for support.
 */
@Singleton
public class AccessService {

    public static final String ADMIN_MODE_HEADER = "X-Admin-Mode";

    private final Set<String> adminEmails;
    private final MembershipRepository memberships;

    public AccessService(@Value("${hyttebok.admin-emails:}") String adminEmails, MembershipRepository memberships) {
        this.adminEmails = Arrays.stream(adminEmails.split(",")).map(e -> e.trim().toLowerCase(Locale.ROOT)).filter(e -> !e.isEmpty()).collect(Collectors.toSet());
        this.memberships = memberships;
    }

    public static Long userId(Authentication authentication) {
        return ((Number) authentication.getAttributes().get("userId")).longValue();
    }

    /**
     * Only for an email Google has confirmed: anyone can sign up with a password using any address, so a
     * password login alone never makes someone admin. Admins log in with Google at least once.
     */
    public boolean isAdmin(Authentication authentication) {
        var email = authentication.getAttributes().get("email");
        return email != null && Boolean.TRUE.equals(authentication.getAttributes().get("emailVerified")) && isAdmin(email.toString());
    }

    public boolean isAdmin(String email) {
        return adminEmails.contains(email.toLowerCase(Locale.ROOT));
    }

    /** An admin who switched admin mode on for this request. */
    public boolean adminMode(Authentication authentication) {
        return isAdmin(authentication) && ServerRequestContext.currentRequest()
            .map(r -> "on".equals(r.getHeaders().get(ADMIN_MODE_HEADER))).orElse(false);
    }

    /** OWNER, MEMBER or null in the given cabin (null in a deleted one). */
    @Nullable
    public String role(Authentication authentication, long cabinId) {
        return memberships.findActive(userId(authentication), cabinId).map(Membership::role).orElse(null);
    }

    /** Role in the cabin of the current request, or null. */
    @Nullable
    public String role(Authentication authentication) {
        return CabinContext.current().map(cabin -> role(authentication, cabin)).orElse(null);
    }

    public boolean canEnter(Authentication authentication, long cabinId) {
        return adminMode(authentication) || role(authentication, cabinId) != null;
    }

    public boolean canManage(Authentication authentication) {
        return adminMode(authentication) || Membership.OWNER.equals(role(authentication));
    }

    void requireManager(Authentication authentication) {
        if (!canManage(authentication)) {
            throw new HttpStatusException(HttpStatus.FORBIDDEN, "Only owners can manage access");
        }
    }

    public void requireAdmin(Authentication authentication) {
        if (!isAdmin(authentication)) {
            throw new HttpStatusException(HttpStatus.FORBIDDEN, "Only system admins can do this");
        }
    }
}
