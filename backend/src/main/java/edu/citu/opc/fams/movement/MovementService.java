package edu.citu.opc.fams.movement;

import edu.citu.opc.fams.asset.Asset;
import edu.citu.opc.fams.asset.AssetCondition;
import edu.citu.opc.fams.asset.AssetCustodyService;
import edu.citu.opc.fams.asset.AssetStatus;
import edu.citu.opc.fams.common.ApiException;
import edu.citu.opc.fams.reference.DepartmentService;
import edu.citu.opc.fams.reference.Employee;
import edu.citu.opc.fams.reference.EmployeeService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * Module 4 Movements and Turnovers. Each public method is ONE database transaction: the asset change
 * and the movement row both commit or both roll back, so the register never disagrees with its
 * history (REQ-306, SRS 2.4 Reliability).
 */
@Service
public class MovementService {

    private final AssetCustodyService custody;
    private final MovementLedger ledger;
    private final DepartmentService departments;
    private final EmployeeService employees;

    public MovementService(AssetCustodyService custody, MovementLedger ledger, DepartmentService departments,
                           EmployeeService employees) {
        this.custody = custody;
        this.ledger = ledger;
        this.departments = departments;
        this.employees = employees;
    }

    /** Which asset ended up moving (the original, or the part split off it). */
    public record Result(Long assetId, Long originalAssetId, boolean split, Long movementId) {
    }

    // ------------------------------------------------------------------ UC-301 Transfer Asset

    @Transactional
    public Result transfer(MovementRequests.Transfer r) {
        Asset a = custody.lock(r.assetId());
        if (a.getStatus() != AssetStatus.ISSUED) {
            throw ApiException.badRequest("Only items assigned to a department can be transferred. "
                    + "Items in OPC storage are released with an issue order.");
        }
        if (!departments.exists(r.toDepartmentId())) {
            throw ApiException.field("toDepartmentId", "Choose a valid department.");
        }
        Employee to = activePersonOf(r.toEmployeeId(), r.toDepartmentId(), "toEmployeeId");
        String toRoom = r.toRoom().trim();
        int qty = checkQuantity(r.quantity(), a);
        if (Objects.equals(a.getDepartmentId(), r.toDepartmentId()) && Objects.equals(a.getRoom(), toRoom)
                && Objects.equals(a.getEmployeeId(), to.getId())) {
            throw ApiException.badRequest("The destination is the same as the source.");
        }

        Long fromDept = a.getDepartmentId();
        Long fromEmp = a.getEmployeeId();
        String fromRoom = a.getRoom();
        boolean split = qty < a.getQuantity();
        Asset moving = split ? custody.split(a, qty) : a;

        custody.move(moving, r.toDepartmentId(), toRoom, to.getId(), AssetStatus.ISSUED, null);
        Movement m = ledger.record(Movement.of(moving.getId(), MovementType.TRANSFER, qty)
                .from(fromDept, fromEmp, fromRoom)
                .to(r.toDepartmentId(), to.getId(), toRoom)
                .note(withSplitNote(r.reason().trim(), split, a)));
        return new Result(moving.getId(), a.getId(), split, m.getId());
    }

    // ------------------------------------------------------------------ UC-302 Condition change or turnover

    @Transactional
    public Result conditionOrTurnover(MovementRequests.Condition r) {
        Asset a = custody.lock(r.assetId());
        if (a.getStatus() == AssetStatus.DRAFT || a.getStatus() == AssetStatus.DISPOSED) {
            throw ApiException.badRequest("This item is " + a.getStatus().label().toLowerCase() + ".");
        }
        int qty = checkQuantity(r.quantity() == null ? a.getQuantity() : r.quantity(), a);
        boolean split = qty < a.getQuantity();

        if (r.action() == MovementRequests.ConditionAction.KEEP) {
            if (r.condition() == a.getCondition() && !split) {
                throw ApiException.field("condition", "The condition is already " + a.getCondition().label() + ".");
            }
            Asset target = split ? custody.split(a, qty) : a;
            custody.move(target, target.getDepartmentId(), target.getRoom(), target.getEmployeeId(),
                    target.getStatus(), r.condition());
            Movement m = ledger.record(Movement.of(target.getId(), MovementType.CONDITION, qty)
                    .from(target.getDepartmentId(), target.getEmployeeId(), target.getRoom())
                    .to(target.getDepartmentId(), target.getEmployeeId(), target.getRoom())
                    .conditionAfter(r.condition().name())
                    .note(withSplitNote(blankToNull(r.note()), split, a)));
            return new Result(target.getId(), a.getId(), split, m.getId());
        }

        // Turn over to OPC storage.
        if (a.getStatus() != AssetStatus.ISSUED) {
            throw ApiException.badRequest("Only items assigned to a department can be turned over.");
        }
        if (r.reason() == null) throw ApiException.field("reason", "Choose the reason.");
        if (r.reason() == TurnoverReason.OTHER && blankToNull(r.note()) == null) {
            throw ApiException.field("note", "Describe the reason.");
        }
        if (r.dateReturned() == null) throw ApiException.field("dateReturned", "Enter the date returned.");
        if (blankToNull(r.turnedOverBy()) == null) {
            throw ApiException.field("turnedOverBy", "Enter who turned it over.");
        }

        Long fromDept = a.getDepartmentId();
        Long fromEmp = a.getEmployeeId();
        String fromRoom = a.getRoom();
        Asset target = split ? custody.split(a, qty) : a;
        custody.move(target, null, null, null, AssetStatus.TURNED_OVER, r.condition());
        Movement m = ledger.record(Movement.of(target.getId(), MovementType.TURNOVER, qty)
                .from(fromDept, fromEmp, fromRoom)
                .conditionAfter(r.condition().name())
                .reason(r.reason().name())
                .note(withSplitNote(blankToNull(r.note()), split, a))
                .turnedOverBy(r.turnedOverBy().trim())
                .dateReturned(r.dateReturned()));
        return new Result(target.getId(), a.getId(), split, m.getId());
    }

    // ------------------------------------------------------------------ UC-302 step 6 Disposal

    @Transactional
    public Result dispose(MovementRequests.Disposal r) {
        Asset a = custody.lock(r.assetId());
        if (a.getStatus() != AssetStatus.TURNED_OVER && a.getStatus() != AssetStatus.IN_STORAGE) {
            throw ApiException.badRequest("Only items held in OPC storage can be disposed of.");
        }
        custody.setStatus(a, AssetStatus.DISPOSED);
        Movement m = ledger.record(Movement.of(a.getId(), MovementType.DISPOSAL, a.getQuantity())
                .from(a.getDepartmentId(), a.getEmployeeId(), a.getRoom())
                .disposalDate(r.disposalDate())
                .note(blankToNull(r.note())));
        return new Result(a.getId(), a.getId(), false, m.getId());
    }

    // ------------------------------------------------------------------ UC-303 one item of a resignation

    /** Called once per item by ResignationService, so each item is its own transaction (UC-303 5a). */
    @Transactional
    public Result resignItem(Long resigningEmployeeId, MovementRequests.ResignationItem item) {
        Asset a = custody.lock(item.assetId());
        if (a.getStatus() != AssetStatus.ISSUED || !Objects.equals(a.getEmployeeId(), resigningEmployeeId)) {
            throw ApiException.badRequest("This item is no longer held by the resigning person.");
        }
        Long fromDept = a.getDepartmentId();
        String fromRoom = a.getRoom();

        Movement m;
        if (item.toEmployeeId() != null) {
            if (item.toEmployeeId().equals(resigningEmployeeId)) {
                throw ApiException.badRequest("Choose someone other than the resigning person.");
            }
            // A new holder in the same department; moving to another department is a transfer.
            Employee to = activePersonOf(item.toEmployeeId(), fromDept, "toEmployeeId");
            custody.move(a, fromDept, fromRoom, to.getId(), AssetStatus.ISSUED, item.condition());
            m = ledger.record(Movement.of(a.getId(), MovementType.RESIGNATION, a.getQuantity())
                    .from(fromDept, resigningEmployeeId, fromRoom)
                    .to(fromDept, to.getId(), fromRoom)
                    .conditionAfter(item.condition().name())
                    .note("Resignation turnover"));
        } else {
            // Back to OPC storage: re-issuable if still usable, otherwise held as turned over.
            boolean usable = item.condition() == AssetCondition.GOOD || item.condition() == AssetCondition.FUNCTIONAL;
            custody.move(a, null, null, null, usable ? AssetStatus.IN_STORAGE : AssetStatus.TURNED_OVER,
                    item.condition());
            m = ledger.record(Movement.of(a.getId(), MovementType.RESIGNATION, a.getQuantity())
                    .from(fromDept, resigningEmployeeId, fromRoom)
                    .conditionAfter(item.condition().name())
                    .reason(usable ? null : item.condition() == AssetCondition.DAMAGED ? "DAMAGED" : "DEFECTIVE")
                    .dateReturned(java.time.LocalDate.now())
                    .note("Resignation turnover to OPC storage"));
        }
        return new Result(a.getId(), a.getId(), false, m.getId());
    }

    // ------------------------------------------------------------------ helpers

    private Employee activePersonOf(Long employeeId, Long departmentId, String field) {
        Employee e = employees.get(employeeId);
        if (!e.isActive()) throw ApiException.field(field, e.getFullName() + " is no longer active.");
        if (e.getDepartmentId() != null && !e.getDepartmentId().equals(departmentId)) {
            throw ApiException.field(field, e.getFullName() + " belongs to another department.");
        }
        return e;
    }

    private static int checkQuantity(Integer qty, Asset a) {
        if (qty == null || qty < 1 || qty > a.getQuantity()) {
            throw ApiException.field("quantity", "Enter a quantity from 1 to " + a.getQuantity() + ".");
        }
        return qty;
    }

    private static String withSplitNote(String note, boolean split, Asset original) {
        if (!split) return note;
        String from = "Split from " + (original.getPropertyTagNo() != null ? original.getPropertyTagNo()
                : "asset #" + original.getId());
        return note == null ? from : note + " · " + from;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
