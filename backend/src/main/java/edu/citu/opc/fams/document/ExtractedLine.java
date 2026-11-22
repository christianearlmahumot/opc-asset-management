package edu.citu.opc.fams.document;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.Map;

/** One item line read by OCR, with how sure the OCR was of each field (0-100). */
@Entity
@Table(name = "extracted_line")
public class ExtractedLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "document_id", nullable = false) private Long documentId;
    @Column(name = "line_no", nullable = false) private int lineNo;
    private Integer quantity;
    @Column(length = 20) private String unit;
    @Column(length = 2500) private String description;
    @Column(name = "unit_cost", precision = 12, scale = 2) private BigDecimal unitCost;
    @Column(name = "total_cost", precision = 14, scale = 2) private BigDecimal totalCost;
    @Column(length = 60) private String category;
    @Column(precision = 5, scale = 2) private BigDecimal confidence;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb") private Map<String, Integer> confidences;
    @Column(nullable = false) private boolean confirmed;
    @Column(name = "asset_id") private Long assetId;

    protected ExtractedLine() {
    }

    ExtractedLine(Long documentId, int lineNo, DocumentParser.Item i) {
        this.documentId = documentId;
        this.lineNo = lineNo;
        this.quantity = i.quantity();
        this.unit = SourceDocument.cut(i.unit(), 20);
        this.description = SourceDocument.cut(i.description(), 2500);
        this.unitCost = i.unitCost();
        this.totalCost = i.totalCost();
        this.category = SourceDocument.cut(i.category(), 60);
        this.confidences = i.confidence();
        this.confidence = BigDecimal.valueOf(i.confidence().values().stream().mapToInt(Integer::intValue).min().orElse(0));
    }

    void confirmedAs(Long assetId) {
        this.confirmed = true;
        this.assetId = assetId;
    }

    public Long getId() { return id; }
    public Long getDocumentId() { return documentId; }
    public int getLineNo() { return lineNo; }
    public Integer getQuantity() { return quantity; }
    public String getUnit() { return unit; }
    public String getDescription() { return description; }
    public BigDecimal getUnitCost() { return unitCost; }
    public BigDecimal getTotalCost() { return totalCost; }
    public String getCategory() { return category; }
    public BigDecimal getConfidence() { return confidence; }
    public Map<String, Integer> getConfidences() { return confidences; }
    public boolean isConfirmed() { return confirmed; }
    public Long getAssetId() { return assetId; }
}
