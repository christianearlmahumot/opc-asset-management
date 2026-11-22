package edu.citu.opc.fams.issueorder;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/** One printed line of an issue order: QTY · ARTICLES / DESCRIPTION · Unit Cost · Total Cost. */
@Entity
@Table(name = "issue_order_line")
public class IssueOrderLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "issue_order_id", nullable = false) private Long issueOrderId;
    @Column(nullable = false) private int quantity;
    @Column(nullable = false, length = 20) private String unit;
    @Column(nullable = false, length = 2500) private String description;
    @Column(name = "unit_cost", precision = 12, scale = 2) private BigDecimal unitCost;
    @Column(name = "total_cost", precision = 14, scale = 2) private BigDecimal totalCost;

    protected IssueOrderLine() {
    }

    IssueOrderLine(Long issueOrderId, int quantity, String unit, String description, BigDecimal unitCost) {
        this.issueOrderId = issueOrderId;
        this.quantity = quantity;
        this.unit = unit;
        this.description = description;
        this.unitCost = unitCost;
        this.totalCost = unitCost == null ? null : unitCost.multiply(BigDecimal.valueOf(quantity));
    }

    public Long getId() { return id; }
    public Long getIssueOrderId() { return issueOrderId; }
    public int getQuantity() { return quantity; }
    public String getUnit() { return unit; }
    public String getDescription() { return description; }
    public BigDecimal getUnitCost() { return unitCost; }
    public BigDecimal getTotalCost() { return totalCost; }
}
