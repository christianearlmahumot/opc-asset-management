package edu.citu.opc.fams.issueorder;

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

    public RequestController(RequestService service, DepartmentService departments) {
        this.service = service;
        this.departments = departments;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public View create(@Valid @RequestBody Form f) {
        return view(service.create(f.departmentId(), f.prNo(), f.purpose(), f.requestedBy(), f.items()), null);
    }

    @GetMapping
    public List<View> list(@RequestParam(required = false) DeptRequest.Status status) {
        List<DeptRequest> list = service.list(status);
        Set<Long> ids = new HashSet<>();
        list.forEach(r -> ids.add(r.getDepartmentId()));
        Map<Long, Department> d = departments.byIds(ids);
        return list.stream().map(r -> view(r, d.get(r.getDepartmentId()))).toList();
    }

    @GetMapping("/{id}")
    public View get(@PathVariable Long id) {
        return view(service.get(id), null);
    }

    @PostMapping("/{id}/cancel")
    public View cancel(@PathVariable Long id) {
        return view(service.cancel(id), null);
    }

    private View view(DeptRequest r, Department d) {
        Department dept = d != null ? d : departments.get(r.getDepartmentId());
        return new View(r.getId(), r.getDepartmentId(), dept.getCode(), r.getPrNo(), r.getPurpose(), r.getRequestedBy(),
                service.items(r), r.getStatus(), r.getCreatedAt(), r.getIssueOrderId());
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
                       OffsetDateTime createdAt, Long issueOrderId) {
    }
}
