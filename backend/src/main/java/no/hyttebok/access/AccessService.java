package no.hyttebok.access;

import io.micronaut.context.annotation.Value;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.security.authentication.Authentication;
import jakarta.inject.Singleton;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Who may do what. System admins come from ADMIN_EMAILS (comma-separated, Google-verified) and manage access,
 * but see the cabin only once they're a member too. Owners invite people and approve requests; members use the app.
 */
@Singleton
public class AccessService {

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

    @Nullable
    public String role(Authentication authentication) {
        return memberships.findById(userId(authentication)).map(Membership::role).orElse(null);
    }

    public boolean canManage(Authentication authentication) {
        return isAdmin(authentication) || Membership.OWNER.equals(role(authentication));
    }

    void requireManager(Authentication authentication) {
        if (!canManage(authentication)) {
            throw new HttpStatusException(HttpStatus.FORBIDDEN, "Only owners and admins can manage access");
        }
    }

    void requireAdmin(Authentication authentication) {
        if (!isAdmin(authentication)) {
            throw new HttpStatusException(HttpStatus.FORBIDDEN, "Only system admins can do this");
        }
    }
}
