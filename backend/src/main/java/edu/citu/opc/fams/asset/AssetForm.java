package edu.citu.opc.fams.asset;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * The Add / Edit Asset form (UI-104, UI-106). Required fields are the ones marked * in the wireframe.
 * On edit, departmentId / room / employeeId are ignored if unchanged and refused if changed (UC-106 3a).
 */
public record AssetForm(
        @NotBlank(message = "Enter the description.") @Size(max = 2500) String description,
        @NotNull(message = "Choose the category.") Long categoryId,
        @Size(max = 40, message = "Use at most 40 characters.") String propertyTagNo,
        @Size(max = 60) String brand,
        @Size(max = 60) String modelNo,
        @Size(max = 60) String serialNo,
        @Size(max = 40) String color,
        @Size(max = 60) String dimension,
        @NotNull(message = "Enter the quantity.") @Min(value = 1, message = "The quantity must be at least 1.")
        @Max(value = 100000, message = "The quantity is too large.") Integer quantity,
        @NotBlank(message = "Choose the unit.") String unit,
        @NotNull(message = "Enter the acquisition date.")
        @PastOrPresent(message = "The acquisition date cannot be in the future.") LocalDate acquisitionDate,
        @NotNull(message = "Enter the acquisition unit cost.")
        @DecimalMin(value = "0.00", message = "The cost cannot be negative.")
        @Digits(integer = 10, fraction = 2, message = "Use at most 2 decimal places.") BigDecimal unitCost,
        @Size(max = 120) String supplier,
        @Size(max = 100) String invoiceNo,
        Long departmentId,
        @Size(max = 80) String room,
        Long employeeId,
        @NotNull(message = "Choose the condition.") AssetCondition condition,
        /** Set when the user saw the duplicate warning and confirmed this is a different item (UC-104 5b). */
        boolean confirmDuplicate) {
}
