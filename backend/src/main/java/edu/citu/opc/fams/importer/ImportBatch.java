package edu.citu.opc.fams.importer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/** UC-107 postcondition: "An import batch record is kept." One row per import run. */
@Entity
@Table(name = "import_batch")
public class ImportBatch {

    public enum Status { RUNNING, DONE, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "file_name", nullable = false) private String fileName;
    @Column(name = "sheet_name", length = 100) private String sheetName;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20) private Status status = Status.RUNNING;
    @Column(name = "total_rows", nullable = false) private int totalRows;
    @Column(name = "imported_rows", nullable = false) private int importedRows;
    @Column(name = "set_aside_rows", nullable = false) private int setAsideRows;
    @Column(name = "rejected_rows", nullable = false) private int rejectedRows;
    @Column(name = "issue_orders", nullable = false) private int issueOrders;
    @Column(name = "created_persons", nullable = false) private int createdPersons;
    @Column(length = 500) private String error;
    @Column(name = "imported_by") private Long importedBy;
    @Column(name = "imported_at", nullable = false) private OffsetDateTime importedAt = OffsetDateTime.now();
    @Column(name = "finished_at") private OffsetDateTime finishedAt;

    protected ImportBatch() {
    }

    ImportBatch(String fileName, String sheetName, int totalRows, Long importedBy) {
        this.fileName = fileName.length() > 255 ? fileName.substring(0, 255) : fileName;
        this.sheetName = sheetName;
        this.totalRows = totalRows;
        this.importedBy = importedBy;
    }

    public Long getId() { return id; }
    public String getFileName() { return fileName; }
    public String getSheetName() { return sheetName; }
    public Status getStatus() { return status; }
    public int getTotalRows() { return totalRows; }
    public int getImportedRows() { return importedRows; }
    public int getSetAsideRows() { return setAsideRows; }
    public int getRejectedRows() { return rejectedRows; }
    public int getIssueOrders() { return issueOrders; }
    public int getCreatedPersons() { return createdPersons; }
    public String getError() { return error; }
    public Long getImportedBy() { return importedBy; }
    public OffsetDateTime getImportedAt() { return importedAt; }
    public OffsetDateTime getFinishedAt() { return finishedAt; }
}
