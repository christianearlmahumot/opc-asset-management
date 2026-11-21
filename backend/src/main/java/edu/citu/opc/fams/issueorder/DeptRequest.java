package edu.citu.opc.fams.issueorder;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/** UC-201: what a department asked for, usually backed by its purchase request (PR). */
@Entity
@Table(name = "dept_request")
public class DeptRequest {

    public enum Status { PENDING, FULFILLED, CANCELLED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "department_id", nullable = false) private Long departmentId;
    @Column(name = "pr_no", length = 40) private String prNo;
    @Column(length = 255) private String purpose;
    /** JSON list of {description, quantity}; kept as text so the request reads like the paper form. */
    @Column(name = "items_needed", columnDefinition = "text") private String itemsNeeded;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Status status = Status.PENDING;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt = OffsetDateTime.now();
    @Column(name = "requested_by", length = 120) private String requestedBy;
    @Column(name = "created_by") private Long createdBy;
    @Column(name = "issue_order_id") private Long issueOrderId;

    protected DeptRequest() {
    }

    DeptRequest(Long departmentId, String prNo, String purpose, String itemsNeeded, String requestedBy, Long createdBy) {
        this.departmentId = departmentId;
        this.prNo = prNo;
        this.purpose = purpose;
        this.itemsNeeded = itemsNeeded;
        this.requestedBy = requestedBy;
        this.createdBy = createdBy;
    }

    void fulfil(Long issueOrderId) {
        this.status = Status.FULFILLED;
        this.issueOrderId = issueOrderId;
    }

    void cancel() {
        this.status = Status.CANCELLED;
    }

    public Long getId() { return id; }
    public Long getDepartmentId() { return departmentId; }
    public String getPrNo() { return prNo; }
    public String getPurpose() { return purpose; }
    public String getItemsNeeded() { return itemsNeeded; }
    public Status getStatus() { return status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public String getRequestedBy() { return requestedBy; }
    public Long getIssueOrderId() { return issueOrderId; }
}
