package edu.citu.opc.fams.audit;

import org.springframework.data.repository.Repository;

/**
 * Append-only by design. It extends the bare {@link Repository}, so it has no update or delete methods
 * (SDD 6.3: "no update or delete methods are exposed"). Read queries for the audit log screen are added in Step 6.
 */
public interface AuditLogRepository extends Repository<AuditLog, Long> {

    AuditLog save(AuditLog entry);
}
