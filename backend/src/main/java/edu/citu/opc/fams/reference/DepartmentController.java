package edu.citu.opc.fams.reference;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Department list for dropdowns. It returns only the code and name: the head's name is personal data
 * (REQ-406) and is exposed in Step 2 with role checks.
 */
@RestController
@RequestMapping("/api/departments")
public class DepartmentController {

    private final DepartmentService service;

    public DepartmentController(DepartmentService service) {
        this.service = service;
    }

    @GetMapping
    public List<DepartmentOption> list() {
        return service.list().stream().map(d -> new DepartmentOption(d.getId(), d.getCode(), d.getName())).toList();
    }

    public record DepartmentOption(Long id, String code, String name) {
    }
}
