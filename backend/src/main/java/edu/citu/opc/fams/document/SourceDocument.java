package edu.citu.opc.fams.document;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * An uploaded sales invoice, purchase order or purchase request (SRS 3.3 source_document). The three
 * documents of one purchase are linked by their PO and PR numbers (UC-101).
 */
@Entity
@Table(name = "source_document")
public class SourceDocument {

    public enum Type {
        SALES_INVOICE("Sales invoice"),
        PURCHASE_ORDER("Purchase order"),
        PURCHASE_REQUEST("Purchase request");

        private final String label;

        Type(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /** QUEUED → PROCESSING → READ (or FAILED) → ENCODED. */
    public enum Status { QUEUED, PROCESSING, READ, FAILED, ENCODED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "doc_type", nullable = false, length = 20) private Type type;
    @Column(name = "doc_no", nullable = false, length = 40) private String docNo;
    @Column(name = "po_no", length = 20) private String poNo;
    @Column(name = "pr_no", length = 40) private String prNo;
    @Column(name = "department_code", length = 20) private String departmentCode;
    @Column(length = 120) private String supplier;
    @Column(name = "doc_date") private LocalDate docDate;
    @Column(name = "file_path", nullable = false, length = 255) private String filePath;
    @Column(name = "original_name", length = 255) private String originalName;
    @Column(name = "content_type", length = 100) private String contentType;
    @Enumerated(EnumType.STRING)
    @Column(name = "ocr_status", nullable = false, length = 20) private Status status = Status.QUEUED;
    @Column(name = "page_count") private Integer pageCount;
    @Column(name = "ocr_text", columnDefinition = "text") private String ocrText;
    @Column(name = "ocr_error", length = 500) private String ocrError;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "header_confidence", columnDefinition = "jsonb") private Map<String, Integer> headerConfidence;
    @Column(name = "ocr_seconds") private Integer ocrSeconds;
    @Column(name = "uploaded_by") private Long uploadedBy;
    @Column(name = "encoding_seconds") private Integer encodingSeconds;
    @Column(name = "encoded_at") private OffsetDateTime encodedAt;
    @Column(name = "encoded_by") private Long encodedBy;
    @Column(name = "created_at", nullable = false, updatable = false) private OffsetDateTime createdAt = OffsetDateTime.now();

    protected SourceDocument() {
    }

    SourceDocument(Type type, String docNo, String filePath, String originalName, String contentType, Long uploadedBy) {
        this.type = type;
        this.docNo = docNo;
        this.filePath = filePath;
        this.originalName = originalName;
        this.contentType = contentType;
        this.uploadedBy = uploadedBy;
    }

    void processing() {
        status = Status.PROCESSING;
        ocrError = null;
    }

    /** The OCR result: pre-fills the header fields that are still empty (the user's entries win). */
    void read(DocumentParser.Result r, int pages, String text, int seconds) {
        status = Status.READ;
        pageCount = pages;
        ocrText = text;
        ocrSeconds = seconds;
        ocrError = null;
        headerConfidence = new LinkedHashMap<>(r.confidence());
        if (poNo == null) poNo = cut(r.poNo(), 20);
        if (prNo == null) prNo = cut(r.prNo(), 40);
        if (departmentCode == null) departmentCode = cut(r.departmentCode(), 20);
        if (supplier == null) supplier = cut(r.supplier(), 120);
        if (docDate == null) docDate = r.docDate();
    }

    void failed(String why, int pages, int seconds) {
        status = Status.FAILED;
        pageCount = pages;
        ocrSeconds = seconds;
        ocrError = cut(why, 500);
    }

    void header(String docNo, String poNo, String prNo, String departmentCode, String supplier, LocalDate docDate) {
        this.docNo = docNo;
        this.poNo = poNo;
        this.prNo = prNo;
        this.departmentCode = departmentCode;
        this.supplier = supplier;
        this.docDate = docDate;
    }

    void encoded(Long userId) {
        status = Status.ENCODED;
        encodedAt = OffsetDateTime.now();
        encodedBy = userId;
        encodingSeconds = (int) Math.max(0, java.time.Duration.between(createdAt, encodedAt).getSeconds());
    }

    Map<String, Object> auditValues() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", type.name());
        m.put("docNo", docNo);
        m.put("poNo", poNo);
        m.put("prNo", prNo);
        m.put("departmentCode", departmentCode);
        m.put("supplier", supplier);
        m.put("docDate", docDate == null ? null : docDate.toString());
        m.put("status", status.name());
        return m;
    }

    static String cut(String s, int max) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty()) return null;
        return t.length() <= max ? t : t.substring(0, max);
    }

    public Long getId() { return id; }
    public Type getType() { return type; }
    public String getDocNo() { return docNo; }
    public String getPoNo() { return poNo; }
    public String getPrNo() { return prNo; }
    public String getDepartmentCode() { return departmentCode; }
    public String getSupplier() { return supplier; }
    public LocalDate getDocDate() { return docDate; }
    public String getFilePath() { return filePath; }
    public String getOriginalName() { return originalName; }
    public String getContentType() { return contentType; }
    public Status getStatus() { return status; }
    public Integer getPageCount() { return pageCount; }
    public String getOcrText() { return ocrText; }
    public String getOcrError() { return ocrError; }
    public Map<String, Integer> getHeaderConfidence() { return headerConfidence; }
    public Integer getOcrSeconds() { return ocrSeconds; }
    public Long getUploadedBy() { return uploadedBy; }
    public Integer getEncodingSeconds() { return encodingSeconds; }
    public OffsetDateTime getEncodedAt() { return encodedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
