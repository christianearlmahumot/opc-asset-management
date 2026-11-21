package edu.citu.opc.fams.access;

import edu.citu.opc.fams.audit.AuditAction;
import edu.citu.opc.fams.audit.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the first Administrator when there are no users yet, from ADMIN_USERNAME and ADMIN_PASSWORD.
 * The password is never hard-coded. If ADMIN_PASSWORD is empty, nothing is created and a warning explains why.
 */
@Component
public class BootstrapAdmin implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdmin.class);

    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final AuditService audit;
    private final String username;
    private final String password;
    private final String fullName;

    public BootstrapAdmin(AppUserRepository users, PasswordEncoder encoder, AuditService audit,
                          @Value("${fams.bootstrap-admin.username}") String username,
                          @Value("${fams.bootstrap-admin.password}") String password,
                          @Value("${fams.bootstrap-admin.full-name}") String fullName) {
        this.users = users;
        this.encoder = encoder;
        this.audit = audit;
        this.username = username;
        this.password = password;
        this.fullName = fullName;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.count() > 0) {
            return;
        }
        if (password == null || password.length() < 8) {
            log.warn("No users exist. Set ADMIN_PASSWORD (8+ characters) and restart to create the first Administrator.");
            return;
        }
        AppUser admin = users.save(new AppUser(username, encoder.encode(password), fullName, Role.ADMIN, null));
        // REQ-404: every create is logged. No one is signed in at startup, so the user is empty (system).
        audit.recordAs(null, AuditAction.CREATE, "app_user", admin.getId(), null, admin.auditValues());
        log.info("Created first Administrator account '{}'.", username);
    }
}
