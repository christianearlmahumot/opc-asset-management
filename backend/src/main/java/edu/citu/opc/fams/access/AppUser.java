package edu.citu.opc.fams.access;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Entity
@Table(name = "app_user")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    /** Only set for Department Head accounts. Kept as an id so access does not depend on the reference module. */
    @Column(name = "department_id")
    private Long departmentId;

    @Column(nullable = false)
    private boolean active = true;

    /** True after an Administrator sets a temporary password (UC-402). */
    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword;

    /** Tokens issued before this moment are refused, so a password change or reset logs out old sessions. */
    @Column(name = "credentials_changed_at", nullable = false)
    private OffsetDateTime credentialsChangedAt = OffsetDateTime.now();

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected AppUser() {
    }

    public AppUser(String username, String passwordHash, String fullName, Role role, Long departmentId) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.role = role;
        this.departmentId = departmentId;
    }

    /** Sets a new password hash and ends every session issued before now. */
    public void changePassword(String newHash, boolean temporary) {
        this.passwordHash = newHash;
        this.mustChangePassword = temporary;
        this.credentialsChangedAt = OffsetDateTime.now();
    }

    /** Audit snapshot. The password hash is never included. */
    public Map<String, Object> auditValues() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("username", username);
        m.put("fullName", fullName);
        m.put("role", role.name());
        m.put("departmentId", departmentId);
        m.put("active", active);
        return m;
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public String getFullName() { return fullName; }
    public Role getRole() { return role; }
    public Long getDepartmentId() { return departmentId; }
    public boolean isActive() { return active; }
    public boolean isMustChangePassword() { return mustChangePassword; }
    public OffsetDateTime getCredentialsChangedAt() { return credentialsChangedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }

    public void setFullName(String fullName) { this.fullName = fullName; }
    public void setRole(Role role) { this.role = role; }
    public void setDepartmentId(Long departmentId) { this.departmentId = departmentId; }
    public void setActive(boolean active) { this.active = active; }
}
