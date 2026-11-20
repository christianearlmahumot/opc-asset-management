package edu.citu.opc.fams.access;

/**
 * The four roles of SRS 2.2. Permissions belong to roles, and each user has exactly one role.
 */
public enum Role {
    ADMIN("Administrator"),
    STAFF("Property Staff"),
    NAS("NAS Assistant"),
    DEPT_HEAD("Department Head");

    private final String label;

    Role(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** Spring Security authority name, e.g. ROLE_STAFF. */
    public String authority() {
        return "ROLE_" + name();
    }
}
