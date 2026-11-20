package edu.citu.opc.fams.reference;

import edu.citu.opc.fams.audit.AuditAction;
import edu.citu.opc.fams.audit.AuditService;
import edu.citu.opc.fams.common.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** The reference module's public face for departments. Other modules use this, never the repository. */
@Service
public class DepartmentService {

    /** Department codes as written in OPC records: MEDTECH, ME, CCS, OPC… */
    private static final Pattern CODE = Pattern.compile("^[A-Z0-9][A-Z0-9-]{0,19}$");

    private final DepartmentRepository repo;
    private final AuditService audit;

    public DepartmentService(DepartmentRepository repo, AuditService audit) {
        this.repo = repo;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<Department> list() {
        return repo.findAllByOrderByCodeAsc();
    }

    @Transactional(readOnly = true)
    public boolean exists(Long id) {
        return id != null && repo.existsById(id);
    }

    @Transactional(readOnly = true)
    public Department get(Long id) {
        return repo.findById(id).orElseThrow(() -> ApiException.notFound("Department"));
    }

    @Transactional(readOnly = true)
    public Optional<Department> findOpc() {
        return repo.findByCodeIgnoreCase(Department.OPC_CODE);
    }

    @Transactional(readOnly = true)
    public Map<Long, Department> byIds(Collection<Long> ids) {
        return repo.findAllById(ids).stream().collect(Collectors.toMap(Department::getId, Function.identity()));
    }

    @Transactional
    public Department create(String code, String name, String headName, String headDesignation) {
        String c = normalizeCode(code);
        if (repo.findByCodeIgnoreCase(c).isPresent()) {
            throw ApiException.field("code", "The department code " + c + " already exists.");
        }
        Department d = repo.save(new Department(c, name.trim(), blankToNull(headName), blankToNull(headDesignation)));
        audit.record(AuditAction.CREATE, "department", d.getId(), null, d.auditValues());
        return d;
    }

    @Transactional
    public Department update(Long id, String code, String name, String headName, String headDesignation) {
        Department d = get(id);
        String c = normalizeCode(code);
        repo.findByCodeIgnoreCase(c).filter(o -> !o.getId().equals(id)).ifPresent(o -> {
            throw ApiException.field("code", "The department code " + c + " already exists.");
        });
        if (Department.OPC_CODE.equalsIgnoreCase(d.getCode()) && !Department.OPC_CODE.equals(c)) {
            throw ApiException.field("code", "The OPC department code cannot be changed.");
        }
        var before = d.auditValues();
        d.setCode(c);
        d.setName(name.trim());
        d.setHeadName(blankToNull(headName));
        d.setHeadDesignation(blankToNull(headDesignation));
        audit.recordChanges("department", d.getId(), before, d.auditValues());
        return d;
    }

    private static String normalizeCode(String code) {
        String c = code == null ? "" : code.trim().toUpperCase();
        if (!CODE.matcher(c).matches()) {
            throw ApiException.field("code", "Use up to 20 capital letters, numbers or dashes, e.g. MEDTECH.");
        }
        return c;
    }

    static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
