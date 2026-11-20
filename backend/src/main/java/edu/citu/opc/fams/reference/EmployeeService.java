package edu.citu.opc.fams.reference;

import edu.citu.opc.fams.access.CurrentUser;
import edu.citu.opc.fams.audit.AuditAction;
import edu.citu.opc.fams.audit.AuditService;
import edu.citu.opc.fams.common.ApiException;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Accountable persons. A Department Head only sees people of their own department (REQ-406). */
@Service
public class EmployeeService {

    private final EmployeeRepository repo;
    private final DepartmentService departments;
    private final AuditService audit;

    public EmployeeService(EmployeeRepository repo, DepartmentService departments, AuditService audit) {
        this.repo = repo;
        this.departments = departments;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<Employee> search(String q, Long departmentId, boolean includeInactive) {
        Long scope = CurrentUser.get().scopedDepartmentId();
        Long dept = scope != null ? scope : departmentId;

        List<Specification<Employee>> specs = new ArrayList<>();
        if (dept != null) {
            specs.add((r, cq, cb) -> cb.equal(r.get("departmentId"), dept));
        }
        if (!includeInactive) {
            specs.add((r, cq, cb) -> cb.equal(r.get("status"), Employee.Status.ACTIVE));
        }
        if (q != null && !q.isBlank()) {
            String like = "%" + q.trim().toLowerCase() + "%";
            specs.add((r, cq, cb) -> cb.or(
                    cb.like(cb.lower(r.get("fullName")), like),
                    cb.like(cb.lower(r.get("employeeNo")), like)));
        }
        return repo.findAll(Specification.allOf(specs), Sort.by("fullName"));
    }

    @Transactional(readOnly = true)
    public Employee get(Long id) {
        return repo.findById(id).orElseThrow(() -> ApiException.notFound("Accountable person"));
    }

    @Transactional(readOnly = true)
    public Map<Long, Employee> byIds(Collection<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        return repo.findAllById(ids).stream().collect(Collectors.toMap(Employee::getId, Function.identity()));
    }

    /** For asset search by holder name. */
    @Transactional(readOnly = true)
    public List<Long> idsByName(String q) {
        return repo.findIdsByNameContaining(q.trim());
    }

    @Transactional
    public Employee create(String employeeNo, String fullName, Long departmentId) {
        checkDepartment(departmentId);
        String no = DepartmentService.blankToNull(employeeNo);
        checkUniqueNo(no, null);
        Employee e = repo.save(new Employee(no, fullName.trim(), departmentId));
        audit.record(AuditAction.CREATE, "employee", e.getId(), null, e.auditValues());
        return e;
    }

    @Transactional
    public Employee update(Long id, String employeeNo, String fullName, Long departmentId, Employee.Status status) {
        Employee e = get(id);
        checkDepartment(departmentId);
        String no = DepartmentService.blankToNull(employeeNo);
        checkUniqueNo(no, id);
        var before = e.auditValues();
        e.setEmployeeNo(no);
        e.setFullName(fullName.trim());
        e.setDepartmentId(departmentId);
        if (status != null) e.setStatus(status);
        audit.recordChanges("employee", id, before, e.auditValues());
        return e;
    }

    private void checkDepartment(Long departmentId) {
        if (departmentId != null && !departments.exists(departmentId)) {
            throw ApiException.field("departmentId", "The selected department does not exist.");
        }
    }

    private void checkUniqueNo(String no, Long selfId) {
        if (no == null) return;
        repo.findByEmployeeNoIgnoreCase(no).filter(o -> !o.getId().equals(selfId)).ifPresent(o -> {
            throw ApiException.field("employeeNo", "Employee no. " + no + " is already used by " + o.getFullName() + ".");
        });
    }
}
