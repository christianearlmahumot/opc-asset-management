package edu.citu.opc.fams.issueorder;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * The OPC issue order (IO): a 7-digit numbered document that releases items to a department and names
 * the accountable person. Names are snapshots of what was printed, since the paper copy is signed.
 */
@Entity
@Table(name = "issue_order")
public class IssueOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "io_no", nullable = false, unique = true, length = 7)
    private String ioNo;

    @Column(name = "request_id") private Long requestId;
    @Column(name = "department_id", nullable = false) private Long departmentId;
    @Column(name = "employee_id") private Long employeeId;
    @Column(name = "issued_by") private Long issuedBy;
    @Column(name = "purchased_from", length = 120) private String purchasedFrom;
    @Column(name = "invoice_no", length = 30) private String invoiceNo;
    @Column(name = "invoice_date") private LocalDate invoiceDate;
    @Column(name = "issued_on", nullable = false) private LocalDate issuedOn;
    @Column(length = 255) private String remarks;
    @Column(name = "pdf_path", length = 255) private String pdfPath;
    @Column(length = 80) private String room;
    @Column(name = "issued_by_name", length = 100) private String issuedByName;
    @Column(name = "approved_by_name", length = 120) private String approvedByName;
    @Column(name = "noted_by_name", length = 120) private String notedByName;
    @Column(name = "noted_by_title", length = 120) private String notedByTitle;
    @Column(name = "received_by_name", length = 120) private String receivedByName;
    @Column(name = "received_by_title", length = 120) private String receivedByTitle;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt = OffsetDateTime.now();

    protected IssueOrder() {
    }

    IssueOrder(String ioNo, Long requestId, Long departmentId, Long employeeId, Long issuedBy, LocalDate issuedOn) {
        this.ioNo = ioNo;
        this.requestId = requestId;
        this.departmentId = departmentId;
        this.employeeId = employeeId;
        this.issuedBy = issuedBy;
        this.issuedOn = issuedOn;
    }

    void purchase(String purchasedFrom, String invoiceNo, LocalDate invoiceDate) {
        this.purchasedFrom = purchasedFrom;
        this.invoiceNo = invoiceNo;
        this.invoiceDate = invoiceDate;
    }

    void signatories(String issuedByName, String approvedByName, String notedByName, String notedByTitle,
                     String receivedByName, String receivedByTitle) {
        this.issuedByName = issuedByName;
        this.approvedByName = approvedByName;
        this.notedByName = notedByName;
        this.notedByTitle = notedByTitle;
        this.receivedByName = receivedByName;
        this.receivedByTitle = receivedByTitle;
    }

    void details(String room, String remarks) {
        this.room = room;
        this.remarks = remarks;
    }

    public Long getId() { return id; }
    public String getIoNo() { return ioNo; }
    public Long getRequestId() { return requestId; }
    public Long getDepartmentId() { return departmentId; }
    public Long getEmployeeId() { return employeeId; }
    public Long getIssuedBy() { return issuedBy; }
    public String getPurchasedFrom() { return purchasedFrom; }
    public String getInvoiceNo() { return invoiceNo; }
    public LocalDate getInvoiceDate() { return invoiceDate; }
    public LocalDate getIssuedOn() { return issuedOn; }
    public String getRemarks() { return remarks; }
    public String getRoom() { return room; }
    public String getIssuedByName() { return issuedByName; }
    public String getApprovedByName() { return approvedByName; }
    public String getNotedByName() { return notedByName; }
    public String getNotedByTitle() { return notedByTitle; }
    public String getReceivedByName() { return receivedByName; }
    public String getReceivedByTitle() { return receivedByTitle; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
