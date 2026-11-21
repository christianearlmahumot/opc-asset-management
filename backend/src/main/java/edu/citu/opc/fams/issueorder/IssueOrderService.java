package edu.citu.opc.fams.issueorder;

import edu.citu.opc.fams.access.CurrentUser;
import edu.citu.opc.fams.asset.Asset;
import edu.citu.opc.fams.asset.AssetCustodyService;
import edu.citu.opc.fams.asset.AssetStatus;
import edu.citu.opc.fams.audit.AuditAction;
import edu.citu.opc.fams.audit.AuditService;
import edu.citu.opc.fams.common.ApiException;
import edu.citu.opc.fams.movement.Movement;
import edu.citu.opc.fams.movement.MovementLedger;
import edu.citu.opc.fams.movement.MovementType;
import edu.citu.opc.fams.reference.Department;
import edu.citu.opc.fams.reference.DepartmentService;
import edu.citu.opc.fams.reference.Employee;
import edu.citu.opc.fams.reference.EmployeeService;
import jakarta.persistence.criteria.Predicate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * UC-202 Select Assets and Generate Issue Order, UC-204 View and Reprint.
 *
 * <p>{@link #prepare} is ONE transaction (SDD 3.2: "in one @Transactional method"): the issue order,
 * its lines, each asset's new custody and each ISSUE movement are saved together or not at all.
 */
@Service
public class IssueOrderService {

    private final IssueOrderRepository orders;
    private final IssueOrderLineRepository lines;
    private final RequestService requests;
    private final AssetCustodyService custody;
    private final MovementLedger ledger;
    private final DepartmentService departments;
    private final EmployeeService employees;
    private final AuditService audit;
    private final long firstNumber;
    private final String custodianName;

    public IssueOrderService(IssueOrderRepository orders, IssueOrderLineRepository lines, RequestService requests,
                             AssetCustodyService custody, MovementLedger ledger,
                             DepartmentService departments, EmployeeService employees, AuditService audit,
                             @Value("${fams.issue-order.first-number}") long firstNumber,
                             @Value("${fams.issue-order.property-custodian-name:}") String custodianName) {
        this.orders = orders;
        this.lines = lines;
        this.requests = requests;
        this.custody = custody;
        this.ledger = ledger;
        this.departments = departments;
        this.employees = employees;
        this.audit = audit;
        this.firstNumber = firstNumber;
        this.custodianName = custodianName == null || custodianName.isBlank() ? null : custodianName.trim();
    }

    public record LineRequest(Long assetId, Integer quantity) {
    }

    public record PrepareRequest(Long requestId, Long departmentId, Long employeeId, String room, String remarks,
                                 String notedByName, String notedByTitle, String receivedByTitle,
                                 List<LineRequest> lines) {
    }

    /** One asset that was no longer available when the user confirmed (UC-202 5a). */
    public record Unavailable(Long assetId, String description, String reason) {
    }

    // ------------------------------------------------------------------ UC-202

    @Transactional
    public IssueOrder prepare(PrepareRequest r) {
        CurrentUser me = CurrentUser.get();
        if (r.lines() == null || r.lines().isEmpty()) {
            throw ApiException.field("lines", "Select at least one item to issue.");
        }
        DeptRequest request = null;
        Long deptId = r.departmentId();
        if (r.requestId() != null) {
            request = requests.get(r.requestId());
            if (request.getStatus() != DeptRequest.Status.PENDING) {
                throw ApiException.badRequest("This request is already " + request.getStatus().name().toLowerCase() + ".");
            }
            deptId = request.getDepartmentId();
        }
        if (deptId == null || !departments.exists(deptId)) {
            throw ApiException.field("departmentId", "Choose the department.");
        }
        Department dept = departments.get(deptId);
        if (r.employeeId() == null) throw ApiException.field("employeeId", "Choose the accountable person.");
        Employee person = employees.get(r.employeeId());
        if (!person.isActive()) throw ApiException.field("employeeId", person.getFullName() + " is no longer active.");
        if (person.getDepartmentId() != null && !person.getDepartmentId().equals(deptId)) {
            throw ApiException.field("employeeId", person.getFullName() + " belongs to another department.");
        }

        // Lock every selected asset in id order (a fixed order avoids deadlocks), then check availability.
        Map<Long, Integer> wanted = new LinkedHashMap<>();
        for (LineRequest l : r.lines()) {
            if (l.assetId() == null) continue;
            wanted.merge(l.assetId(), l.quantity() == null ? Integer.MAX_VALUE : l.quantity(), (a, b) -> a);
        }
        List<Long> ids = wanted.keySet().stream().sorted().toList();
        List<Asset> locked = new ArrayList<>();
        List<Unavailable> unavailable = new ArrayList<>();
        for (Long id : ids) {
            Asset a = custody.lock(id);
            if (a.getStatus() != AssetStatus.IN_STORAGE) {
                unavailable.add(new Unavailable(a.getId(), a.getDescription(), "now " + a.getStatus().label().toLowerCase()));
            } else {
                locked.add(a);
            }
        }
        if (!unavailable.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT,
                    unavailable.size() == 1 ? "One selected item is no longer available. It was removed from the selection."
                            : unavailable.size() + " selected items are no longer available. They were removed from the selection.",
                    Map.of(), Map.of("unavailable", unavailable));
        }

        // Next number, under a lock so it is never handed out twice.
        orders.lockNumbering();
        String ioNo = nextNumber();

        IssueOrder io = new IssueOrder(ioNo, request == null ? null : request.getId(), deptId, person.getId(), me.id(),
                LocalDate.now());
        List<Asset> issued = new ArrayList<>();
        for (Asset a : locked) {
            int q = wanted.get(a.getId()) == Integer.MAX_VALUE ? a.getQuantity() : wanted.get(a.getId());
            if (q < 1 || q > a.getQuantity()) {
                throw ApiException.field("lines", "Quantity for " + a.getDescription() + " must be from 1 to " + a.getQuantity() + ".");
            }
            issued.add(q < a.getQuantity() ? custody.split(a, q) : a);
        }
        io.purchase(joined(issued.stream().map(Asset::getSupplier).toList(), 120),
                joined(issued.stream().map(Asset::getInvoiceNo).toList(), 30),
                issued.stream().map(Asset::getAcquisitionDate).filter(Objects::nonNull).min(Comparator.naturalOrder()).orElse(null));
        String receivedTitle = blank(r.receivedByTitle()) != null ? r.receivedByTitle().trim()
                : (dept.getHeadDesignation() != null ? dept.getHeadDesignation() + ", " + dept.getCode() : dept.getCode());
        io.signatories(me.fullName(), custodianName, blank(r.notedByName()), blank(r.notedByTitle()),
                person.getFullName(), receivedTitle);
        io.details(blank(r.room()), blank(r.remarks()));
        orders.save(io);

        String room = blank(r.room());
        for (Asset a : issued) {
            IssueOrderLine line = lines.save(new IssueOrderLine(io.getId(), a.getQuantity(), a.getUnit(),
                    a.getDescription(), a.getUnitCost()));
            custody.issue(a, deptId, room, person.getId(), line.getId());
            ledger.record(Movement.of(a.getId(), MovementType.ISSUE, a.getQuantity())
                    .to(deptId, person.getId(), room)
                    .issueOrder(io.getId())
                    .note("IO " + ioNo));
        }
        if (request != null) {
            requests.markFulfilled(request, io.getId());
        }

        Map<String, Object> v = new LinkedHashMap<>();
        v.put("ioNo", ioNo);
        v.put("departmentId", deptId);
        v.put("employeeId", person.getId());
        v.put("lines", issued.size());
        v.put("requestId", io.getRequestId());
        audit.record(AuditAction.CREATE, "issue_order", io.getId(), null, v);
        return io;
    }

    /** The number the next issue order will get (shown before confirming; the real one is fixed at save). */
    @Transactional(readOnly = true)
    public String previewNextNumber() {
        return nextNumber();
    }

    private String nextNumber() {
        String max = orders.maxIoNo();
        long next = max == null ? firstNumber : Math.max(Long.parseLong(max.trim()) + 1, firstNumber);
        if (next > 9_999_999L) throw new IllegalStateException("Issue order numbers have run out of 7 digits");
        return String.format("%07d", next);
    }

    // ------------------------------------------------------------------ UC-204

    public record Filter(String ioNo, Long departmentId, LocalDate from, LocalDate to) {
    }

    @Transactional(readOnly = true)
    public Page<IssueOrder> search(Filter f, int page, int size) {
        Specification<IssueOrder> spec = (root, q, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (f.ioNo() != null && !f.ioNo().isBlank()) p.add(cb.like(root.get("ioNo"), f.ioNo().trim() + "%"));
            if (f.departmentId() != null) p.add(cb.equal(root.get("departmentId"), f.departmentId()));
            if (f.from() != null) p.add(cb.greaterThanOrEqualTo(root.get("issuedOn"), f.from()));
            if (f.to() != null) p.add(cb.lessThanOrEqualTo(root.get("issuedOn"), f.to()));
            return cb.and(p.toArray(Predicate[]::new));
        };
        return orders.findAll(spec, PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "issuedOn").and(Sort.by(Sort.Direction.DESC, "ioNo"))));
    }

    @Transactional(readOnly = true)
    public Map<Long, Long> lineCounts(List<Long> orderIds) {
        Map<Long, Long> m = new LinkedHashMap<>();
        if (orderIds.isEmpty()) return m;
        for (Object[] row : lines.countByOrders(orderIds)) m.put((Long) row[0], (Long) row[1]);
        return m;
    }

    @Transactional(readOnly = true)
    public IssueOrder get(Long id) {
        return orders.findById(id).orElseThrow(() -> ApiException.notFound("Issue order"));
    }

    @Transactional(readOnly = true)
    public List<IssueOrderLine> linesOf(Long id) {
        return lines.findByIssueOrderIdOrderByIdAsc(id);
    }

    private static String joined(List<String> values, int max) {
        Set<String> distinct = new LinkedHashSet<>();
        for (String v : values) if (v != null && !v.isBlank()) distinct.add(v.trim());
        if (distinct.isEmpty()) return null;
        String s = String.join(" / ", distinct);
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
