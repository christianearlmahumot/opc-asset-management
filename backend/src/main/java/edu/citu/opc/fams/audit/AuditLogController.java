package edu.citu.opc.fams.audit;

import edu.citu.opc.fams.access.UserDirectory;
import edu.citu.opc.fams.common.ApiException;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** SDD 6.3: GET /api/audit-logs, Administrator only. Read-only: there is no write, edit or delete here. */
@RestController
@RequestMapping("/api/audit-logs")
@PreAuthorize("hasRole('ADMIN')")
public class AuditLogController {

    private final AuditQueryService service;
    private final UserDirectory users;

    public AuditLogController(AuditQueryService service, UserDirectory users) {
        this.service = service;
        this.users = users;
    }

    /** UC-404 steps 2-3: filter by user, action, record type or date range; newest first. */
    @GetMapping
    public PageOf<Entry> search(@RequestParam(required = false) Long userId,
                                @RequestParam(required = false) AuditAction action,
                                @RequestParam(required = false) String entity,
                                @RequestParam(required = false) Long entityId,
                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                @RequestParam(defaultValue = "0") int page,
                                @RequestParam(defaultValue = "25") int size) {
        if (from != null && to != null && from.isAfter(to)) {
            throw ApiException.field("to", "The end date cannot be before the start date.");
        }
        Page<AuditLog> p = service.search(new AuditQueryService.Filter(userId, action, entity, entityId, from, to),
                page, size);
        Set<Long> ids = new HashSet<>();
        p.forEach(a -> { if (a.getUserId() != null) ids.add(a.getUserId()); });
        Map<Long, String> names = users.namesByIds(ids);
        List<Entry> rows = p.getContent().stream().map(a -> Entry.of(a, names)).toList();
        return new PageOf<>(rows, p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages());
    }

    /** UC-404 step 4: one entry with its old and new values. */
    @GetMapping("/{id}")
    public Entry get(@PathVariable Long id) {
        AuditLog a = service.get(id).orElseThrow(() -> ApiException.notFound("Audit entry"));
        Map<Long, String> names = a.getUserId() == null ? Map.of() : users.namesByIds(List.of(a.getUserId()));
        return Entry.of(a, names);
    }

    /** The record types that appear in the log, for the filter list. */
    @GetMapping("/entities")
    public List<String> entities() {
        return service.entities();
    }

    public record Entry(Long id, OffsetDateTime at, Long userId, String userName, AuditAction action,
                        String entity, Long entityId, Map<String, Object> oldValues, Map<String, Object> newValues) {
        static Entry of(AuditLog a, Map<Long, String> names) {
            return new Entry(a.getId(), a.getCreatedAt(), a.getUserId(),
                    a.getUserId() == null ? null : names.get(a.getUserId()), a.getAction(), a.getEntity(),
                    a.getEntityId(), a.getOldValues(), a.getNewValues());
        }
    }

    public record PageOf<T>(List<T> items, int page, int size, long total, int totalPages) {
    }
}
