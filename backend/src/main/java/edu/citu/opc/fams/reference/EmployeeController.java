package edu.citu.opc.fams.reference;

import edu.citu.opc.fams.access.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Accountable persons. Maintained by OPC staff (SRS 2.5); Department Heads see their own department only. */
@RestController
@RequestMapping("/api/employees")
public class EmployeeController {

    private final EmployeeService service;

    public EmployeeController(EmployeeService service) {
        this.service = service;
    }

    @GetMapping
    public List<EmployeeView> search(@RequestParam(required = false) String q,
                                     @RequestParam(required = false) Long departmentId,
                                     @RequestParam(defaultValue = "false") boolean includeInactive) {
        return service.search(q, departmentId, includeInactive).stream().map(EmployeeView::of).toList();
    }

    @GetMapping("/{id}")
    public EmployeeView get(@PathVariable Long id) {
        Employee e = service.get(id);
        CurrentUser.get().checkCanSee(e.getDepartmentId(), "Accountable person");
        return EmployeeView.of(e);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    @ResponseStatus(HttpStatus.CREATED)
    public EmployeeView create(@Valid @RequestBody EmployeeForm f) {
        return EmployeeView.of(service.create(f.employeeNo(), f.fullName(), f.departmentId()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    public EmployeeView update(@PathVariable Long id, @Valid @RequestBody EmployeeForm f) {
        return EmployeeView.of(service.update(id, f.employeeNo(), f.fullName(), f.departmentId(), f.status()));
    }

    public record EmployeeView(Long id, String employeeNo, String fullName, Long departmentId, Employee.Status status) {
        static EmployeeView of(Employee e) {
            return new EmployeeView(e.getId(), e.getEmployeeNo(), e.getFullName(), e.getDepartmentId(), e.getStatus());
        }
    }

    public record EmployeeForm(
            @Size(max = 20) String employeeNo,
            @NotBlank(message = "Enter the full name.") @Size(max = 150) String fullName,
            Long departmentId,
            Employee.Status status) {
    }
}
