package edu.citu.opc.fams.issueorder;

import edu.citu.opc.fams.document.PurchaseLookup;
import edu.citu.opc.fams.reference.Department;
import edu.citu.opc.fams.reference.DepartmentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** SDD 3.1: /api/requests. Administrators and Property Staff. */
@RestController
@RequestMapping("/api/requests")
@PreAuthorize("hasAnyRole('ADMIN','STAFF')")
public class RequestController {

    private final RequestService service;
    private final DepartmentService departments;
    private final PurchaseLookup purchases;

    public RequestController(RequestService service, DepartmentService departments, PurchaseLookup purchases) {
        this.service = service;
        this.departments = departments;
        this.purchases = purchases;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public View create(@Valid @RequestBody Form f) {
        return view(service.create(f.departmentId(), f.prNo(), f.purpose(), f.requestedBy(), f.items()), null, true);
    }

    @GetMapping
    public List<View> list(@RequestParam(required = false) DeptRequest.Status status) {
        List<DeptRequest> list = service.list(status);
        Set<Long> ids = new HashSet<>();
        list.forEach(r -> ids.add(r.getDepartmentId()));
        Map<Long, Department> d = departments.byIds(ids);
        return list.stream().map(r -> view(r, d.get(r.getDepartmentId()), false)).toList();
    }

    @GetMapping("/{id}")
    public View get(@PathVariable Long id) {
        return view(service.get(id), null, true);
    }

    @PostMapping("/{id}/cancel")
    public View cancel(@PathVariable Long id) {
        return view(service.cancel(id), null, true);
    }

    private View view(DeptRequest r, Department d, boolean withDocuments) {
        Department dept = d != null ? d : departments.get(r.getDepartmentId());
        // UC-201 step 4: the uploaded PR with the same number, and the PO and invoices of that purchase.
        List<PurchaseLookup.Linked> docs = withDocuments && r.getPrNo() != null && !r.getPrNo().isBlank()
                ? purchases.linked(null, r.getPrNo()) : List.of();
        return new View(r.getId(), r.getDepartmentId(), dept.getCode(), r.getPrNo(), r.getPurpose(), r.getRequestedBy(),
                service.items(r), r.getStatus(), r.getCreatedAt(), r.getIssueOrderId(), docs);
    }

    public record Form(
            @NotNull(message = "Choose the department.") Long departmentId,
            @Size(max = 40) String prNo,
            @Size(max = 255) String purpose,
            @Size(max = 120) String requestedBy,
            List<RequestService.Item> items) {
    }

    public record View(Long id, Long departmentId, String departmentCode, String prNo, String purpose,
                       String requestedBy, List<RequestService.Item> items, DeptRequest.Status status,
                       OffsetDateTime createdAt, Long issueOrderId, List<PurchaseLookup.Linked> documents) {
    }
}
