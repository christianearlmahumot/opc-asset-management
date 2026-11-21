package edu.citu.opc.fams.asset;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One asset item (SRS 3.3): either a single tracked unit with a property tag and serial number, or a
 * quantity of identical items (e.g. 49 laboratory stools). Fields follow the Property Inventory Slip.
 *
 * <p>Custody (department, room, accountable person) has no public setters: it changes only through a
 * movement (UC-106 3a, REQ-306).
 */
@Entity
@Table(name = "asset")
public class Asset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "property_tag_no", unique = true, length = 40) private String propertyTagNo;
    @Column(nullable = false, length = 500) private String description;
    @Column(name = "category_id") private Long categoryId;
    @Column(length = 60) private String brand;
    @Column(name = "model_no", length = 60) private String modelNo;
    @Column(name = "serial_no", length = 60) private String serialNo;
    @Column(length = 40) private String color;
    @Column(length = 60) private String dimension;
    @Column(nullable = false) private int quantity;
    @Column(nullable = false, length = 20) private String unit;
    @Column(name = "acquisition_date") private LocalDate acquisitionDate;
    @Column(name = "unit_cost", precision = 12, scale = 2) private BigDecimal unitCost;
    @Column(length = 120) private String supplier;
    @Column(name = "invoice_no", length = 30) private String invoiceNo;

    @Column(name = "department_id") private Long departmentId;
    @Column(length = 80) private String room;
    @Column(name = "employee_id") private Long employeeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "asset_condition", nullable = false, length = 20)
    private AssetCondition condition = AssetCondition.GOOD;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AssetStatus status = AssetStatus.IN_STORAGE;

    @Column(name = "issue_order_line_id") private Long issueOrderLineId;
    @Column(name = "parent_asset_id") private Long parentAssetId;
    @Column(name = "source_document_id") private Long sourceDocumentId;
    @Column(name = "photo_path", length = 255) private String photoPath;
    @Column(name = "created_by") private Long createdBy;
    @Column(name = "created_at", nullable = false, updatable = false) private OffsetDateTime createdAt = OffsetDateTime.now();
    @Column(name = "updated_at", nullable = false) private OffsetDateTime updatedAt = OffsetDateTime.now();

    /** Optimistic lock: two people changing the same asset at once cannot overwrite each other. */
    @Version
    private long version;

    protected Asset() {
    }

    Asset(Long createdBy) {
        this.createdBy = createdBy;
    }

    @PreUpdate
    void touch() {
        this.updatedAt = OffsetDateTime.now();
    }

    /** Sets custody when the asset is first encoded. Later changes go through movements only. */
    void placeInitially(Long departmentId, String room, Long employeeId) {
        this.departmentId = departmentId;
        this.room = room;
        this.employeeId = employeeId;
    }

    /** Changes custody. Only AssetCustodyService calls this, always together with a movement (REQ-306). */
    void moveCustody(Long departmentId, String room, Long employeeId) {
        this.departmentId = departmentId;
        this.room = room;
        this.employeeId = employeeId;
    }

    /** Links the asset to the issue order line that released it. */
    void assignIssueOrderLine(Long lineId) {
        this.issueOrderLineId = lineId;
    }

    /**
     * Splits {@code qty} off this item as a new asset (UC-301 step 5: "splits off the transferred quantity
     * as a new item"). The new item copies the descriptive fields, has no tag or serial (those identify
     * the original), and points back to this one.
     */
    Asset splitOff(int qty, Long createdBy) {
        if (qty <= 0 || qty >= quantity) {
            throw new IllegalArgumentException("Split quantity must be between 1 and " + (quantity - 1));
        }
        Asset c = new Asset(createdBy);
        c.description = description;
        c.categoryId = categoryId;
        c.brand = brand;
        c.modelNo = modelNo;
        c.color = color;
        c.dimension = dimension;
        c.quantity = qty;
        c.unit = unit;
        c.acquisitionDate = acquisitionDate;
        c.unitCost = unitCost;
        c.supplier = supplier;
        c.invoiceNo = invoiceNo;
        c.departmentId = departmentId;
        c.room = room;
        c.employeeId = employeeId;
        c.condition = condition;
        c.status = status;
        c.issueOrderLineId = issueOrderLineId;
        c.sourceDocumentId = sourceDocumentId;
        c.parentAssetId = id;
        this.quantity -= qty;
        return c;
    }

    /** Snapshot for the audit log (old and new values, REQ-404). */
    Map<String, Object> auditValues() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("propertyTagNo", propertyTagNo);
        m.put("description", description);
        m.put("categoryId", categoryId);
        m.put("brand", brand);
        m.put("modelNo", modelNo);
        m.put("serialNo", serialNo);
        m.put("color", color);
        m.put("dimension", dimension);
        m.put("quantity", quantity);
        m.put("unit", unit);
        m.put("acquisitionDate", acquisitionDate == null ? null : acquisitionDate.toString());
        m.put("unitCost", unitCost == null ? null : unitCost.toPlainString());
        m.put("supplier", supplier);
        m.put("invoiceNo", invoiceNo);
        m.put("departmentId", departmentId);
        m.put("room", room);
        m.put("employeeId", employeeId);
        m.put("condition", condition.name());
        m.put("status", status.name());
        m.put("photoPath", photoPath);
        m.put("parentAssetId", parentAssetId);
        return m;
    }

    public Long getId() { return id; }
    public String getPropertyTagNo() { return propertyTagNo; }
    public String getDescription() { return description; }
    public Long getCategoryId() { return categoryId; }
    public String getBrand() { return brand; }
    public String getModelNo() { return modelNo; }
    public String getSerialNo() { return serialNo; }
    public String getColor() { return color; }
    public String getDimension() { return dimension; }
    public int getQuantity() { return quantity; }
    public String getUnit() { return unit; }
    public LocalDate getAcquisitionDate() { return acquisitionDate; }
    public BigDecimal getUnitCost() { return unitCost; }
    public String getSupplier() { return supplier; }
    public String getInvoiceNo() { return invoiceNo; }
    public Long getDepartmentId() { return departmentId; }
    public String getRoom() { return room; }
    public Long getEmployeeId() { return employeeId; }
    public AssetCondition getCondition() { return condition; }
    public AssetStatus getStatus() { return status; }
    public Long getIssueOrderLineId() { return issueOrderLineId; }
    public Long getParentAssetId() { return parentAssetId; }
    public Long getSourceDocumentId() { return sourceDocumentId; }
    public String getPhotoPath() { return photoPath; }
    public Long getCreatedBy() { return createdBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }

    void setPropertyTagNo(String v) { this.propertyTagNo = v; }
    void setDescription(String v) { this.description = v; }
    void setCategoryId(Long v) { this.categoryId = v; }
    void setBrand(String v) { this.brand = v; }
    void setModelNo(String v) { this.modelNo = v; }
    void setSerialNo(String v) { this.serialNo = v; }
    void setColor(String v) { this.color = v; }
    void setDimension(String v) { this.dimension = v; }
    void setQuantity(int v) { this.quantity = v; }
    void setUnit(String v) { this.unit = v; }
    void setAcquisitionDate(LocalDate v) { this.acquisitionDate = v; }
    void setUnitCost(BigDecimal v) { this.unitCost = v; }
    void setSupplier(String v) { this.supplier = v; }
    void setInvoiceNo(String v) { this.invoiceNo = v; }
    void setCondition(AssetCondition v) { this.condition = v; }
    void setStatus(AssetStatus v) { this.status = v; }
    void setPhotoPath(String v) { this.photoPath = v; }
}
