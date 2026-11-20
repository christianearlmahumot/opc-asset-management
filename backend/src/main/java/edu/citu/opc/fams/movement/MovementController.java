package edu.citu.opc.fams.movement;

import edu.citu.opc.fams.access.UserDirectory;
import edu.citu.opc.fams.asset.Asset;
import edu.citu.opc.fams.asset.AssetCustodyService;
import edu.citu.opc.fams.asset.MovementText;
import edu.citu.opc.fams.reference.Department;
import edu.citu.opc.fams.reference.DepartmentService;
import edu.citu.opc.fams.reference.Employee;
import edu.citu.opc.fams.reference.EmployeeService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** SDD Module 4 endpoints. Recording movements is for Administrators and Property Staff only (SRS 2.2). */
@RestController
@RequestMapping("/api")
@PreAuthorize("hasAnyRole('ADMIN','STAFF')")
public class MovementController {

    private final MovementService movements;
    private final ResignationService resignations;
    private final MovementLedger ledger;
    private final AssetCustodyService custody;
    private final DepartmentService departments;
    private final EmployeeService employees;
    private final UserDirectory users;

    public MovementController(MovementService movements, ResignationService resignations, MovementLedger ledger,
                              AssetCustodyService custody, DepartmentService departments, EmployeeService employees,
                              UserDirectory users) {
        this.movements = movements;
        this.resignations = resignations;
        this.ledger = ledger;
        this.custody = custody;
        this.departments = departments;
        this.employees = employees;
        this.users = users;
    }

    @PostMapping("/movements/transfer")
    public MovementService.Result transfer(@Valid @RequestBody MovementRequests.Transfer r) {
        return movements.transfer(r);
    }

    @PostMapping("/movements/turnover")
    public MovementService.Result turnover(@Valid @RequestBody MovementRequests.Condition r) {
        return movements.conditionOrTurnover(r);
    }

    @PostMapping("/movements/disposal")
    public MovementService.Result disposal(@Valid @RequestBody MovementRequests.Disposal r) {
        return movements.dispose(r);
    }

    @PostMapping("/movements/resignation")
    public ResignationService.Outcome resignation(@Valid @RequestBody MovementRequests.Resignation r) {
        return resignations.process(r);
    }

    /** UC-303 step 2: the items assigned to a person, grouped by department and room. */
    @GetMapping("/employees/{id}/assets")
    public List<HeldItem> heldBy(@PathVariable Long id) {
        employees.get(id);
        List<Asset> held = custody.heldBy(id);
        Set<Long> deptIds = new HashSet<>();
        held.forEach(a -> { if (a.getDepartmentId() != null) deptIds.add(a.getDepartmentId()); });
        Map<Long, Department> d = departments.byIds(deptIds);
        return held.stream().map(a -> new HeldItem(a.getId(), a.getPropertyTagNo(), a.getDescription(),
                a.getQuantity(), a.getUnit(), a.getDepartmentId(),
                a.getDepartmentId() == null ? null : d.get(a.getDepartmentId()).getCode(), a.getRoom(),
                a.getCondition().name(), a.getCondition().label())).toList();
    }

    /** Recent movements across all assets (the Movements page). */
    @GetMapping("/movements")
    public PageOf<Row> list(@RequestParam(required = false) MovementType type,
                            @RequestParam(required = false) Long departmentId,
                            @RequestParam(defaultValue = "0") int page,
                            @RequestParam(defaultValue = "25") int size) {
        Page<Movement> p = ledger.search(type, departmentId, page, size);
        Set<Long> assetIds = new HashSet<>();
        Set<Long> deptIds = new HashSet<>();
        Set<Long> empIds = new HashSet<>();
        Set<Long> userIds = new HashSet<>();
        for (Movement m : p) {
            assetIds.add(m.getAssetId());
            addAll(deptIds, m.getFromDepartmentId(), m.getToDepartmentId());
            addAll(empIds, m.getFromEmployeeId(), m.getToEmployeeId());
            addAll(userIds, m.getRecordedBy());
        }
        Map<Long, AssetCustodyService.Brief> a = custody.briefs(assetIds);
        Map<Long, Department> d = departments.byIds(deptIds);
        Map<Long, Employee> e = employees.byIds(empIds);
        Map<Long, String> u = users.namesByIds(userIds);
        List<Row> rows = p.getContent().stream().map(m -> {
            var b = a.get(m.getAssetId());
            return new Row(m.getId(), m.getRecordedAt(), m.getType().name(), m.getType().label(), m.getAssetId(),
                    b == null ? null : b.propertyTagNo(), b == null ? null : b.description(), m.getQuantity(),
                    b == null ? null : b.unit(), MovementText.summary(m, d, e), u.get(m.getRecordedBy()));
        }).toList();
        return new PageOf<>(rows, p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages());
    }

    private static void addAll(Set<Long> s, Long... ids) {
        for (Long id : ids) if (id != null) s.add(id);
    }

    public record HeldItem(Long id, String propertyTagNo, String description, int quantity, String unit,
                           Long departmentId, String departmentCode, String room, String condition,
                           String conditionLabel) {
    }

    public record Row(Long id, OffsetDateTime at, String type, String typeLabel, Long assetId, String propertyTagNo,
                      String description, int quantity, String unit, String summary, String recordedBy) {
    }

    public record PageOf<T>(List<T> items, int page, int size, long total, int totalPages) {
    }
}
