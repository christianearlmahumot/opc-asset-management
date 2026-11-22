package edu.citu.opc.fams.reference;

import edu.citu.opc.fams.audit.AuditAction;
import edu.citu.opc.fams.audit.AuditService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * UC-107 step 3-4: how the masterlist's spellings map to the clean reference values. A mapping is saved
 * once ("COLLIB" → LIBRARY-COLLEGE, "copies" → copy) and every later import reuses it.
 */
@Service
public class ValueMappingService {

    private final ValueMappingRepository repo;
    private final DepartmentRepository departments;
    private final AuditService audit;

    public ValueMappingService(ValueMappingRepository repo, DepartmentRepository departments, AuditService audit) {
        this.repo = repo;
        this.departments = departments;
        this.audit = audit;
    }

    /** Lower-case, single spaces: the key every lookup uses. */
    public static String key(String s) {
        return s == null ? "" : s.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    /**
     * Every department label that already resolves: through a saved mapping, or because it is a
     * department's code or name. Keyed by {@link #key}.
     */
    @Transactional(readOnly = true)
    public Map<String, Department> departmentLookup() {
        Map<String, Department> m = new HashMap<>();
        List<Department> all = departments.findAll();
        Map<String, Department> byCode = new HashMap<>();
        for (Department d : all) {
            byCode.put(key(d.getCode()), d);
            m.put(key(d.getName()), d);
        }
        m.putAll(byCode);   // a code wins over a name that happens to be spelled the same
        for (ValueMapping v : repo.findAll()) {
            if (v.getKind() != ValueMapping.Kind.DEPARTMENT) continue;
            Department d = byCode.get(key(v.getMappedValue()));
            if (d != null) m.put(key(v.getSourceValue()), d);
        }
        return m;
    }

    /** Every unit spelling that already resolves, keyed by {@link #key}, to its clean unit. */
    @Transactional(readOnly = true)
    public Map<String, String> unitLookup() {
        Map<String, String> m = new HashMap<>();
        for (ValueMapping v : repo.findAll()) {
            if (v.getKind() == ValueMapping.Kind.UNIT) m.put(key(v.getSourceValue()), v.getMappedValue());
        }
        return m;
    }

    @Transactional
    public void mapDepartment(String label, Department to) {
        save(ValueMapping.Kind.DEPARTMENT, label, to.getCode());
    }

    @Transactional
    public void mapUnit(String label, String unit) {
        save(ValueMapping.Kind.UNIT, label, unit);
    }

    private void save(ValueMapping.Kind kind, String label, String value) {
        String source = label.trim().replaceAll("\\s+", " ");
        if (source.isEmpty() || repo.find(kind, source).isPresent()) return;
        ValueMapping m = repo.save(new ValueMapping(kind, source.length() > 255 ? source.substring(0, 255) : source, value));
        audit.record(AuditAction.CREATE, "value_mapping", m.getId(), null,
                Map.of("kind", kind.name(), "value", m.getSourceValue(), "mappedTo", value));
    }
}
