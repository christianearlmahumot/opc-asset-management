package edu.citu.opc.fams.access;

import edu.citu.opc.fams.audit.AuditAction;
import edu.citu.opc.fams.audit.AuditService;
import edu.citu.opc.fams.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * UC-401 Log In, plus changing one's own password. Passwords are checked against BCrypt hashes, and a
 * JWT is issued on success.
 */
@Service
public class AuthService {

    /** UC-401 2a: never say whether the username or the password was wrong. */
    static final String INVALID = "Invalid username or password.";

    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final AuditService audit;

    /** Compared against when the username is unknown, so both failure paths take the same time. */
    private final String dummyHash;

    public AuthService(AppUserRepository users, PasswordEncoder encoder, JwtService jwt, AuditService audit) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
        this.audit = audit;
        this.dummyHash = encoder.encode("timing-equalizer-not-a-real-password");
    }

    /**
     * Not @Transactional on purpose: the LOGIN_FAILED entry must commit even though the login then
     * throws. Each audit write runs in its own short transaction.
     */
    public LoginResult login(String username, String password) {
        AppUser user = users.findByUsernameIgnoreCase(username.trim()).orElse(null);
        boolean ok = encoder.matches(password, user != null ? user.getPasswordHash() : dummyHash);

        if (user == null || !ok || !user.isActive()) {
            // The attempted username is logged so repeated attempts are visible. The password never is.
            audit.recordAs(user != null ? user.getId() : null, AuditAction.LOGIN_FAILED, "app_user",
                    user != null ? user.getId() : null, null, Map.of("username", username.trim()));
            throw new ApiException(HttpStatus.UNAUTHORIZED, INVALID);
        }

        audit.recordAs(user.getId(), AuditAction.LOGIN, "app_user", user.getId(), null, null);
        return new LoginResult(jwt.issue(user), user);
    }

    /**
     * Changes the signed-in user's own password. This also ends their other sessions, so a fresh token
     * is returned for this one.
     */
    @Transactional
    public LoginResult changeOwnPassword(String currentPassword, String newPassword) {
        AppUser user = users.findById(CurrentUser.get().id()).orElseThrow(() -> ApiException.notFound("User"));
        if (!encoder.matches(currentPassword, user.getPasswordHash())) {
            throw ApiException.badRequest("Your current password is not correct.");
        }
        if (currentPassword.equals(newPassword)) {
            throw ApiException.badRequest("Choose a password different from the current one.");
        }
        PasswordPolicy.check(newPassword, user.getUsername());
        user.changePassword(encoder.encode(newPassword), false);
        audit.record(AuditAction.UPDATE, "app_user", user.getId(), null, Map.of("password", "changed by user"));
        return new LoginResult(jwt.issue(user), user);
    }

    public record LoginResult(String token, AppUser user) {
    }
}
