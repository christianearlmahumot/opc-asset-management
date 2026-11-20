package edu.citu.opc.fams.reference;

import edu.citu.opc.fams.audit.AuditAction;
import edu.citu.opc.fams.audit.AuditService;
import edu.citu.opc.fams.common.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Units of measure. Every unit written to an asset goes through {@link #normalize}, so "pcs", "pc"
 * and "pieces" are all stored as "pc" (UC-102 step 6) and the 74 masterlist spellings cannot come back.
 */
@Service
public class UnitService {

    private static final Pattern UNIT = Pattern.compile("^[a-z][a-z .-]{0,19}$");

    private final ValueMappingRepository repo;
    private final AuditService audit;

    public UnitService(ValueMappingRepository repo, AuditService audit) {
        this.repo = repo;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<String> list() {
        return repo.mappedValues(ValueMapping.Kind.UNIT);
    }

    /** Returns the clean unit, or rejects a unit the OPC has not added to the list. */
    @Transactional(readOnly = true)
    public String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            throw ApiException.field("unit", "Choose the unit.");
        }
        return repo.find(ValueMapping.Kind.UNIT, raw.trim())
                .map(ValueMapping::getMappedValue)
                .orElseThrow(() -> ApiException.field("unit",
                        "The unit '" + raw.trim() + "' is not in the list. An Administrator can add it."));
    }

    @Transactional
    public String add(String unit) {
        String u = unit.trim().toLowerCase();
        if (!UNIT.matcher(u).matches()) {
            throw ApiException.field("unit", "Use up to 20 letters, e.g. ream or bottle.");
        }
        if (repo.find(ValueMapping.Kind.UNIT, u).isPresent()) {
            throw ApiException.field("unit", "The unit '" + u + "' already exists.");
        }
        ValueMapping m = repo.save(new ValueMapping(ValueMapping.Kind.UNIT, u, u));
        audit.record(AuditAction.CREATE, "value_mapping", m.getId(), null, Map.of("kind", "UNIT", "value", u));
        return u;
    }
}
