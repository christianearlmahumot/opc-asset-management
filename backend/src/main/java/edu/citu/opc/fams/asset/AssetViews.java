package edu.citu.opc.fams.asset;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/** The JSON shapes the asset endpoints return (SDD: DTOs). */
public final class AssetViews {

    private AssetViews() {
    }

    /** A row of the search results (UI-205). */
    public record Row(Long id, String propertyTagNo, String description, String serialNo, int quantity, String unit,
                      Long departmentId, String departmentCode, String room, Long employeeId, String accountable,
                      AssetCondition condition, String conditionLabel, AssetStatus status, String statusLabel) {
    }

    /** Everything about one asset (asset detail page and edit form). */
    public record Detail(Long id, String propertyTagNo, String description, Long categoryId, String categoryName,
                         String brand, String modelNo, String serialNo, String color, String dimension,
                         int quantity, String unit, LocalDate acquisitionDate, BigDecimal unitCost,
                         BigDecimal totalCost, String supplier, String invoiceNo,
                         Long departmentId, String departmentCode, String departmentName, String room,
                         Long employeeId, String accountable,
                         AssetCondition condition, String conditionLabel, AssetStatus status, String statusLabel,
                         boolean hasPhoto, Long parentAssetId, String createdByName,
                         OffsetDateTime createdAt, OffsetDateTime updatedAt) {
    }

    /** One line of the movement history, newest first (UC-205 step 5). */
    public record HistoryEntry(Long id, OffsetDateTime at, String type, String typeLabel, int quantity,
                               String summary, String recordedBy) {
    }
}
