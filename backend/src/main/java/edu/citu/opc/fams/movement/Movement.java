package edu.citu.opc.fams.movement;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * One ledger entry: a recorded change in an asset's department, holder or condition (SRS 1.3).
 * Movements are never edited; a correction is a new movement. Conditions and reasons are kept as
 * enum names so this module does not depend on the asset module.
 */
@Entity
@Immutable
@Table(name = "movement")
public class Movement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "asset_id", nullable = false)
    private Long assetId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MovementType type;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "from_department_id") private Long fromDepartmentId;
    @Column(name = "to_department_id") private Long toDepartmentId;
    @Column(name = "from_employee_id") private Long fromEmployeeId;
    @Column(name = "to_employee_id") private Long toEmployeeId;
    @Column(name = "from_room", length = 80) private String fromRoom;
    @Column(name = "to_room", length = 80) private String toRoom;
    @Column(name = "condition_after", length = 20) private String conditionAfter;
    @Column(length = 20) private String reason;
    @Column(length = 255) private String note;
    @Column(name = "turned_over_by", length = 100) private String turnedOverBy;
    @Column(name = "date_returned") private LocalDate dateReturned;
    @Column(name = "disposal_date") private LocalDate disposalDate;
    @Column(name = "issue_order_id") private Long issueOrderId;
    @Column(name = "recorded_by") private Long recordedBy;
    @Column(name = "recorded_at", nullable = false) private OffsetDateTime recordedAt;

    protected Movement() {
    }

    private Movement(Long assetId, MovementType type, int quantity) {
        this.assetId = assetId;
        this.type = type;
        this.quantity = quantity;
        this.recordedAt = OffsetDateTime.now();
    }

    public static Movement of(Long assetId, MovementType type, int quantity) {
        return new Movement(assetId, type, quantity);
    }

    public Movement from(Long departmentId, Long employeeId, String room) {
        this.fromDepartmentId = departmentId;
        this.fromEmployeeId = employeeId;
        this.fromRoom = room;
        return this;
    }

    public Movement to(Long departmentId, Long employeeId, String room) {
        this.toDepartmentId = departmentId;
        this.toEmployeeId = employeeId;
        this.toRoom = room;
        return this;
    }

    public Movement conditionAfter(String c) { this.conditionAfter = c; return this; }
    public Movement reason(String r) { this.reason = r; return this; }
    public Movement note(String n) { this.note = n; return this; }
    public Movement turnedOverBy(String t) { this.turnedOverBy = t; return this; }
    public Movement dateReturned(LocalDate d) { this.dateReturned = d; return this; }
    public Movement disposalDate(LocalDate d) { this.disposalDate = d; return this; }
    public Movement issueOrder(Long id) { this.issueOrderId = id; return this; }
    Movement recordedBy(Long userId) { this.recordedBy = userId; return this; }

    public Long getId() { return id; }
    public Long getAssetId() { return assetId; }
    public MovementType getType() { return type; }
    public int getQuantity() { return quantity; }
    public Long getFromDepartmentId() { return fromDepartmentId; }
    public Long getToDepartmentId() { return toDepartmentId; }
    public Long getFromEmployeeId() { return fromEmployeeId; }
    public Long getToEmployeeId() { return toEmployeeId; }
    public String getFromRoom() { return fromRoom; }
    public String getToRoom() { return toRoom; }
    public String getConditionAfter() { return conditionAfter; }
    public String getReason() { return reason; }
    public String getNote() { return note; }
    public String getTurnedOverBy() { return turnedOverBy; }
    public LocalDate getDateReturned() { return dateReturned; }
    public LocalDate getDisposalDate() { return disposalDate; }
    public Long getIssueOrderId() { return issueOrderId; }
    public Long getRecordedBy() { return recordedBy; }
    public OffsetDateTime getRecordedAt() { return recordedAt; }
}
