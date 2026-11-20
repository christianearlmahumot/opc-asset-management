package edu.citu.opc.fams.movement;

import edu.citu.opc.fams.asset.AssetCondition;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/** Request bodies of the movement endpoints (SDD Module 4). */
public final class MovementRequests {

    private MovementRequests() {
    }

    /** UI-301 Transfer Asset. */
    public record Transfer(
            @NotNull Long assetId,
            @NotNull(message = "Choose the destination department.") Long toDepartmentId,
            @NotBlank(message = "Enter the destination room.") @Size(max = 80) String toRoom,
            @NotNull(message = "Choose the accountable person.") Long toEmployeeId,
            @NotNull(message = "Enter the quantity to move.") @Min(value = 1, message = "At least 1.") Integer quantity,
            @NotBlank(message = "Enter the reason.") @Size(max = 255) String reason) {
    }

    public enum ConditionAction { KEEP, TURN_OVER }

    /** UI-302 Record Condition or Turnover. */
    public record Condition(
            @NotNull Long assetId,
            @NotNull(message = "Choose the new condition.") AssetCondition condition,
            @NotNull(message = "Choose keep or turn over.") ConditionAction action,
            TurnoverReason reason,
            @Size(max = 255) String note,
            @PastOrPresent(message = "The date returned cannot be in the future.") LocalDate dateReturned,
            @Size(max = 100) String turnedOverBy,
            @Min(value = 1, message = "At least 1.") Integer quantity) {
    }

    /** UC-302 step 6: the disposal date of a turned-over item. */
    public record Disposal(
            @NotNull Long assetId,
            @NotNull(message = "Enter the disposal date.")
            @PastOrPresent(message = "The disposal date cannot be in the future.") LocalDate disposalDate,
            @Size(max = 255) String note) {
    }

    /** UI-303 Resignation Turnover: one row per item. toEmployeeId null means OPC storage. */
    public record ResignationItem(
            @NotNull Long assetId,
            Long toEmployeeId,
            @NotNull(message = "Choose the condition.") AssetCondition condition) {
    }

    public record Resignation(
            @NotNull Long employeeId,
            @NotEmpty(message = "There are no items to turn over.") List<@Valid ResignationItem> items,
            boolean markResigned) {
    }
}
