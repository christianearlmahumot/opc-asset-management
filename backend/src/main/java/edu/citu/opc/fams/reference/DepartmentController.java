package edu.citu.opc.fams.reference;

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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Departments. Everyone can read the code list; Administrators manage it (SRS 2.2). */
@RestController
@RequestMapping("/api/departments")
public class DepartmentController {

    private final DepartmentService service;

    public DepartmentController(DepartmentService service) {
        this.service = service;
    }

    /** Code and name only, for dropdowns. The head's name is personal data (REQ-406). */
    @GetMapping
    public List<DepartmentOption> list() {
        return service.list().stream().map(d -> new DepartmentOption(d.getId(), d.getCode(), d.getName())).toList();
    }

    @GetMapping("/details")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    public List<DepartmentView> details() {
        return service.list().stream().map(DepartmentView::of).toList();
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public DepartmentView create(@Valid @RequestBody DepartmentForm f) {
        return DepartmentView.of(service.create(f.code(), f.name(), f.headName(), f.headDesignation()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public DepartmentView update(@PathVariable Long id, @Valid @RequestBody DepartmentForm f) {
        return DepartmentView.of(service.update(id, f.code(), f.name(), f.headName(), f.headDesignation()));
    }

    public record DepartmentOption(Long id, String code, String name) {
    }

    public record DepartmentView(Long id, String code, String name, String headName, String headDesignation) {
        static DepartmentView of(Department d) {
            return new DepartmentView(d.getId(), d.getCode(), d.getName(), d.getHeadName(), d.getHeadDesignation());
        }
    }

    public record DepartmentForm(
            @NotBlank(message = "Enter the department code.") @Size(max = 20) String code,
            @NotBlank(message = "Enter the department name.") @Size(max = 120) String name,
            @Size(max = 100) String headName,
            @Size(max = 60) String headDesignation) {
    }
}
