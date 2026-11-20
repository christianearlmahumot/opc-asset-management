package edu.citu.opc.fams.access;

import edu.citu.opc.fams.audit.AuditAction;
import edu.citu.opc.fams.audit.AuditService;
import edu.citu.opc.fams.common.ApiException;
import edu.citu.opc.fams.reference.DepartmentService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * UC-402 Manage User Accounts and Roles (Administrator only). This is how people are registered in
 * the system: there is no self-signup, because every account needs a role chosen by the OPC (REQ-403, REQ-406).
 */
@Service
public class UserService {

    private static final Pattern USERNAME = Pattern.compile("^[a-zA-Z0-9._-]{3,50}$");

    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final AuditService audit;
    private final DepartmentService departments;

    public UserService(AppUserRepository users, PasswordEncoder encoder, AuditService audit,
                       DepartmentService departments) {
        this.users = users;
        this.encoder = encoder;
        this.audit = audit;
        this.departments = departments;
    }

    @Transactional(readOnly = true)
    public List<AppUser> list() {
        return users.findAllByOrderByActiveDescFullNameAsc();
    }

    @Transactional
    public AppUser create(String fullName, String username, String tempPassword, Role role, Long departmentId) {
        String uname = username.trim();
        if (!USERNAME.matcher(uname).matches()) {
            throw fieldError("Use 3 to 50 letters, numbers, dots, dashes or underscores.");
        }
        if (users.existsByUsernameIgnoreCase(uname)) {
            throw ApiException.conflict("The username '" + uname + "' is already taken. Choose another.");  // UC-402 4a
        }
        PasswordPolicy.check(tempPassword, uname);

        AppUser u = new AppUser(uname, encoder.encode(tempPassword), fullName.trim(), role,
                checkedDepartment(role, departmentId));
        u.changePassword(u.getPasswordHash(), true);   // temporary: the user must choose their own at first login
        users.save(u);
        audit.record(AuditAction.CREATE, "app_user", u.getId(), null, u.auditValues());
        return u;
    }

    @Transactional
    public AppUser update(Long id, String fullName, Role role, Long departmentId) {
        AppUser u = find(id);
        CurrentUser me = CurrentUser.get();
        if (u.getId().equals(me.id()) && role != u.getRole()) {
            throw ApiException.badRequest("You cannot change your own role. Ask another Administrator.");
        }
        if (u.getRole() == Role.ADMIN && role != Role.ADMIN && u.isActive()) {
            ensureAnotherActiveAdmin();
        }
        var before = u.auditValues();
        u.setFullName(fullName.trim());
        u.setRole(role);
        u.setDepartmentId(checkedDepartment(role, departmentId));
        audit.recordChanges("app_user", u.getId(), before, u.auditValues());
        return u;
    }

    /** UC-402 step 5: a deactivated user can no longer sign in, and their open sessions stop working at once. */
    @Transactional
    public AppUser setActive(Long id, boolean active) {
        AppUser u = find(id);
        if (!active && u.getId().equals(CurrentUser.get().id())) {
            throw ApiException.badRequest("You cannot deactivate your own account.");   // UC-402 5a
        }
        if (!active && u.getRole() == Role.ADMIN && u.isActive()) {
            ensureAnotherActiveAdmin();
        }
        if (u.isActive() == active) {
            return u;
        }
        var before = u.auditValues();
        u.setActive(active);
        audit.recordChanges("app_user", u.getId(), before, u.auditValues());
        return u;
    }

    /** Sets a new temporary password, for example when a user forgets theirs, and ends their sessions. */
    @Transactional
    public AppUser resetPassword(Long id, String tempPassword) {
        AppUser u = find(id);
        PasswordPolicy.check(tempPassword, u.getUsername());
        u.changePassword(encoder.encode(tempPassword), true);
        audit.record(AuditAction.UPDATE, "app_user", u.getId(), null,
                Map.of("password", "reset to a temporary password by an administrator"));
        return u;
    }

    private AppUser find(Long id) {
        return users.findById(id).orElseThrow(() -> ApiException.notFound("User"));
    }

    /** A Department Head must have one department (UC-402). Other roles have none. */
    private Long checkedDepartment(Role role, Long departmentId) {
        if (role != Role.DEPT_HEAD) {
            return null;
        }
        if (departmentId == null) {
            throw ApiException.badRequest("Choose the department this Department Head is responsible for.");
        }
        if (!departments.exists(departmentId)) {
            throw ApiException.badRequest("The selected department does not exist.");
        }
        return departmentId;
    }

    private void ensureAnotherActiveAdmin() {
        if (users.countByRoleAndActiveTrue(Role.ADMIN) <= 1) {
            throw ApiException.badRequest("This is the only active Administrator. Make another Administrator first.");
        }
    }

    private static ApiException fieldError(String msg) {
        return ApiException.badRequest("Username: " + msg);
    }
}
