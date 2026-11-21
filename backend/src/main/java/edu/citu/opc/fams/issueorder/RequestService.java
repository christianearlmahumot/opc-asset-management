package edu.citu.opc.fams.issueorder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.citu.opc.fams.access.CurrentUser;
import edu.citu.opc.fams.audit.AuditAction;
import edu.citu.opc.fams.audit.AuditService;
import edu.citu.opc.fams.common.ApiException;
import edu.citu.opc.fams.reference.DepartmentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** UC-201 Record Department Request. */
@Service
public class RequestService {

    public record Item(String description, int quantity) {
    }

    private final DeptRequestRepository repo;
    private final DepartmentService departments;
    private final AuditService audit;
    private final ObjectMapper json;

    public RequestService(DeptRequestRepository repo, DepartmentService departments, AuditService audit,
                          ObjectMapper json) {
        this.repo = repo;
        this.departments = departments;
        this.audit = audit;
        this.json = json;
    }

    @Transactional
    public DeptRequest create(Long departmentId, String prNo, String purpose, String requestedBy, List<Item> items) {
        if (!departments.exists(departmentId)) throw ApiException.field("departmentId", "Choose the department.");
        List<Item> clean = items == null ? List.of() : items.stream()
                .filter(i -> i.description() != null && !i.description().isBlank())
                .map(i -> new Item(i.description().trim(), i.quantity())).toList();
        if (clean.isEmpty()) throw ApiException.field("items", "Add at least one item.");
        if (clean.stream().anyMatch(i -> i.quantity() < 1)) throw ApiException.field("items", "Each quantity must be at least 1.");

        DeptRequest r = repo.save(new DeptRequest(departmentId, blank(prNo), blank(purpose), write(clean),
                blank(requestedBy), CurrentUser.get().id()));
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("departmentId", departmentId);
        v.put("prNo", r.getPrNo());
        v.put("purpose", r.getPurpose());
        v.put("items", clean.size());
        audit.record(AuditAction.CREATE, "dept_request", r.getId(), null, v);
        return r;
    }

    @Transactional(readOnly = true)
    public List<DeptRequest> list(DeptRequest.Status status) {
        return status == null ? repo.findTop100ByOrderByCreatedAtDesc() : repo.findByStatusOrderByCreatedAtDesc(status);
    }

    @Transactional(readOnly = true)
    public DeptRequest get(Long id) {
        return repo.findById(id).orElseThrow(() -> ApiException.notFound("Request"));
    }

    @Transactional
    public DeptRequest cancel(Long id) {
        DeptRequest r = get(id);
        if (r.getStatus() != DeptRequest.Status.PENDING) throw ApiException.badRequest("Only pending requests can be cancelled.");
        r.cancel();
        audit.recordChanges("dept_request", id, Map.of("status", "PENDING"), Map.of("status", "CANCELLED"));
        return r;
    }

    /**
     * UC-202 postcondition: the request is marked Fulfilled by its issue order. Runs inside the
     * issue order's transaction (MANDATORY), so the status change and its audit entry (REQ-404)
     * commit or roll back with the issue order.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void markFulfilled(DeptRequest r, Long issueOrderId) {
        Map<String, Object> before = new LinkedHashMap<>();
        before.put("status", r.getStatus().name());
        before.put("issueOrderId", r.getIssueOrderId());
        r.fulfil(issueOrderId);
        repo.save(r);
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("status", r.getStatus().name());
        after.put("issueOrderId", r.getIssueOrderId());
        audit.recordChanges("dept_request", r.getId(), before, after);
    }

    public List<Item> items(DeptRequest r) {
        if (r.getItemsNeeded() == null) return List.of();
        try {
            return json.readValue(r.getItemsNeeded(), new TypeReference<>() { });
        } catch (JsonProcessingException e) {
            return List.of(new Item(r.getItemsNeeded(), 1));
        }
    }

    private String write(List<Item> items) {
        try {
            return json.writeValueAsString(items);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
