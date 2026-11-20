package edu.citu.opc.fams.audit;

import edu.citu.opc.fams.access.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Writes the audit trail for every create, edit and delete (REQ-404). Every module calls it inside its
 * own transaction, so the change and its audit entry commit or roll back together.
 */
@Service
public class AuditService {

    private final AuditLogRepository repo;

    public AuditService(AuditLogRepository repo) {
        this.repo = repo;
    }

    /** Records an action by the user of the current request. */
    @Transactional
    public void record(AuditAction action, String entity, Long entityId,
                       Map<String, ?> oldValues, Map<String, ?> newValues) {
        Long userId = CurrentUser.find().map(CurrentUser::id).orElse(null);
        recordAs(userId, action, entity, entityId, oldValues, newValues);
    }

    /** Records an action for a known user id. Login uses it, because no token exists yet at that point. */
    @Transactional
    public void recordAs(Long userId, AuditAction action, String entity, Long entityId,
                         Map<String, ?> oldValues, Map<String, ?> newValues) {
        repo.save(new AuditLog(userId, action, entity, entityId, copy(oldValues), copy(newValues)));
    }

    /**
     * Records an UPDATE with only the fields that actually changed, which keeps the log readable.
     * Nothing is written when nothing changed.
     */
    @Transactional
    public void recordChanges(String entity, Long entityId, Map<String, ?> before, Map<String, ?> after) {
        Map<String, Object> oldDiff = new LinkedHashMap<>();
        Map<String, Object> newDiff = new LinkedHashMap<>();
        for (String key : after.keySet()) {
            Object o = before.get(key);
            Object n = after.get(key);
            if (!Objects.equals(o, n)) {
                oldDiff.put(key, o);
                newDiff.put(key, n);
            }
        }
        if (!newDiff.isEmpty()) {
            record(AuditAction.UPDATE, entity, entityId, oldDiff, newDiff);
        }
    }

    private static Map<String, Object> copy(Map<String, ?> m) {
        return m == null ? null : new LinkedHashMap<>(m);
    }
}
