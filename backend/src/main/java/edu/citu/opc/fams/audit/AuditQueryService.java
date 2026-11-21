package edu.citu.opc.fams.audit;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * UC-404 View Audit Log: read-only queries over audit_log. It uses the JPA criteria API directly
 * instead of JpaSpecificationExecutor, because that interface also brings delete(Specification),
 * and the audit log must expose no update or delete (SDD 6.3, REQ-404).
 */
@Service
public class AuditQueryService {

    /** Dates in the filter are office dates (SRS: the OPC is in Cebu). */
    private static final ZoneId OFFICE_ZONE = ZoneId.of("Asia/Manila");

    @PersistenceContext
    private EntityManager em;

    /** All filters are optional. {@code from} and {@code to} are inclusive whole days. */
    public record Filter(Long userId, AuditAction action, String entity, Long entityId,
                         LocalDate from, LocalDate to) {
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> search(Filter f, int page, int size) {
        int p = Math.max(page, 0);
        int s = Math.min(Math.max(size, 1), 100);
        CriteriaBuilder cb = em.getCriteriaBuilder();

        CriteriaQuery<AuditLog> q = cb.createQuery(AuditLog.class);
        Root<AuditLog> r = q.from(AuditLog.class);
        q.where(predicates(cb, r, f)).orderBy(cb.desc(r.get("createdAt")), cb.desc(r.get("id")));
        List<AuditLog> rows = em.createQuery(q).setFirstResult(p * s).setMaxResults(s).getResultList();

        CriteriaQuery<Long> c = cb.createQuery(Long.class);
        Root<AuditLog> cr = c.from(AuditLog.class);
        c.select(cb.count(cr)).where(predicates(cb, cr, f));
        long total = em.createQuery(c).getSingleResult();

        return new PageImpl<>(rows, PageRequest.of(p, s), total);
    }

    @Transactional(readOnly = true)
    public Optional<AuditLog> get(Long id) {
        return Optional.ofNullable(em.find(AuditLog.class, id));
    }

    /** The record types that appear in the log, for the filter list. */
    @Transactional(readOnly = true)
    public List<String> entities() {
        return em.createQuery("select distinct a.entity from AuditLog a order by a.entity", String.class)
                .getResultList();
    }

    private static Predicate[] predicates(CriteriaBuilder cb, Root<AuditLog> r, Filter f) {
        List<Predicate> p = new ArrayList<>();
        if (f.userId() != null) p.add(cb.equal(r.get("userId"), f.userId()));
        if (f.action() != null) p.add(cb.equal(r.get("action"), f.action()));
        if (f.entity() != null && !f.entity().isBlank()) p.add(cb.equal(r.get("entity"), f.entity().trim()));
        if (f.entityId() != null) p.add(cb.equal(r.get("entityId"), f.entityId()));
        if (f.from() != null) {
            OffsetDateTime start = f.from().atStartOfDay(OFFICE_ZONE).toOffsetDateTime();
            p.add(cb.greaterThanOrEqualTo(r.get("createdAt"), start));
        }
        if (f.to() != null) {
            OffsetDateTime end = f.to().plusDays(1).atStartOfDay(OFFICE_ZONE).toOffsetDateTime();
            p.add(cb.lessThan(r.get("createdAt"), end));
        }
        return p.toArray(Predicate[]::new);
    }
}
