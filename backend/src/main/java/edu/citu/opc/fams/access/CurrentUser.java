package edu.citu.opc.fams.access;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * The signed-in user of the current request, loaded by {@link JwtFilter}.
 */
public record CurrentUser(Long id, String username, String fullName, Role role, Long departmentId,
                          boolean mustChangePassword) {

    public boolean is(Role r) {
        return role == r;
    }

    /** The user of the current request, if any. Empty during login or background jobs. */
    public static Optional<CurrentUser> find() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CurrentUser cu) {
            return Optional.of(cu);
        }
        return Optional.empty();
    }

    /** The user of the current request. Only call it from authenticated endpoints. */
    public static CurrentUser get() {
        return find().orElseThrow(() -> new IllegalStateException("No authenticated user"));
    }
}
