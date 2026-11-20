package edu.citu.opc.fams.access;

import edu.citu.opc.fams.common.ApiException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Objects;
import java.util.Optional;

/**
 * The signed-in user of the current request, loaded by {@link JwtFilter}.
 */
public record CurrentUser(Long id, String username, String fullName, Role role, Long departmentId,
                          boolean mustChangePassword) {

    public boolean is(Role r) {
        return role == r;
    }

    /**
     * REQ-406: a Department Head only ever sees their own department. Returns that department for a
     * Department Head, and null (no restriction) for every other role.
     */
    public Long scopedDepartmentId() {
        return role == Role.DEPT_HEAD ? departmentId : null;
    }

    /** Throws 404 if a Department Head asks for a record of another department. */
    public void checkCanSee(Long recordDepartmentId, String what) {
        if (role == Role.DEPT_HEAD && !Objects.equals(departmentId, recordDepartmentId)) {
            throw ApiException.notFound(what);   // 404, not 403: do not reveal that it exists
        }
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
