package edu.citu.opc.fams.report;

import edu.citu.opc.fams.access.CurrentUser;
import edu.citu.opc.fams.asset.Asset;
import edu.citu.opc.fams.asset.AssetService;
import edu.citu.opc.fams.asset.AssetStatus;
import edu.citu.opc.fams.reference.Department;
import edu.citu.opc.fams.reference.DepartmentService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * UC-304 View Department Inventory Report (SDD 5.1 SummaryService): the department's current items,
 * grouped by room, with quantity and cost totals. Computed from the asset register, which every
 * movement updates in the same transaction, so the summary always agrees with the masterlist.
 */
@Service
public class SummaryService {

    static final String NO_ROOM = "No room recorded";
    static final String OPC_STORAGE = "OPC storage";
    static final String TURNED_OVER = "Turned-over items";

    private final AssetService assets;
    private final DepartmentService departments;
    private final String institution;

    public SummaryService(AssetService assets, DepartmentService departments,
                          @Value("${fams.issue-order.institution}") String institution) {
        this.assets = assets;
        this.departments = departments;
        this.institution = institution;
    }

    public record Line(Long assetId, String propertyTagNo, int quantity, String unit, String description,
                       String brand, String modelNo, String serialNo, Integer year, BigDecimal unitCost,
                       BigDecimal totalCost, String remarks) {
    }

    public record RoomGroup(String room, int itemCount, long totalQuantity, BigDecimal totalCost, List<Line> lines) {
    }

    public record DepartmentSummary(Long departmentId, String departmentCode, String departmentName,
                                    String headName, String headDesignation, String institution, LocalDate asOf,
                                    int itemCount, long totalQuantity, BigDecimal totalCost,
                                    int itemsWithoutCost, List<RoomGroup> rooms) {
    }

    @Transactional(readOnly = true)
    public DepartmentSummary departmentSummary(Long departmentId) {
        Department d = departments.get(departmentId);   // 404 if it does not exist
        // REQ-406: a Department Head sees only their own department (404, so others are not revealed).
        CurrentUser.get().checkCanSee(d.getId(), "Department");

        // Group by room; rooms in alphabetical order, the OPC's own groups and "no room" last.
        Map<String, List<Asset>> byRoom = new TreeMap<>(Comparator
                .comparingInt(SummaryService::roomRank)
                .thenComparing(r -> r.toLowerCase(Locale.ROOT)));
        for (Asset a : assets.currentInDepartment(d.getId())) {
            byRoom.computeIfAbsent(roomOf(a), k -> new ArrayList<>()).add(a);
        }

        List<RoomGroup> rooms = new ArrayList<>();
        int itemCount = 0;
        long totalQty = 0;
        BigDecimal totalCost = BigDecimal.ZERO;
        int withoutCost = 0;
        for (var e : byRoom.entrySet()) {
            List<Asset> items = e.getValue();
            items.sort(Comparator.comparing((Asset a) -> a.getDescription().toLowerCase(Locale.ROOT))
                    .thenComparing(Asset::getId));
            List<Line> lines = new ArrayList<>();
            long roomQty = 0;
            BigDecimal roomCost = BigDecimal.ZERO;
            for (Asset a : items) {
                BigDecimal total = a.getUnitCost() == null ? null
                        : a.getUnitCost().multiply(BigDecimal.valueOf(a.getQuantity()));
                if (total == null) withoutCost++;
                else roomCost = roomCost.add(total);
                roomQty += a.getQuantity();
                lines.add(new Line(a.getId(), a.getPropertyTagNo(), a.getQuantity(), a.getUnit(), a.getDescription(),
                        a.getBrand(), a.getModelNo(), a.getSerialNo(),
                        a.getAcquisitionDate() == null ? null : a.getAcquisitionDate().getYear(),
                        a.getUnitCost(), total, remarksOf(a)));
            }
            rooms.add(new RoomGroup(e.getKey(), lines.size(), roomQty, roomCost, lines));
            itemCount += lines.size();
            totalQty += roomQty;
            totalCost = totalCost.add(roomCost);
        }

        // UC-304 2a: a department with no assets gets an empty report with zero totals.
        return new DepartmentSummary(d.getId(), d.getCode(), d.getName(), d.getHeadName(), d.getHeadDesignation(),
                institution, LocalDate.now(ZoneId.of("Asia/Manila")), itemCount, totalQty, totalCost, withoutCost, rooms);
    }

    private static String roomOf(Asset a) {
        if (a.getDepartmentId() == null) {
            return a.getStatus() == AssetStatus.TURNED_OVER ? TURNED_OVER : OPC_STORAGE;
        }
        return a.getRoom() == null || a.getRoom().isBlank() ? NO_ROOM : a.getRoom().trim();
    }

    private static int roomRank(String room) {
        return switch (room) {
            case OPC_STORAGE -> 1;
            case TURNED_OVER -> 2;
            case NO_ROOM -> 3;
            default -> 0;
        };
    }

    /** The Remarks column: the condition as written on OPC forms, and whether it was turned over. */
    private static String remarksOf(Asset a) {
        String condition = a.getCondition().label();
        return a.getStatus() == AssetStatus.TURNED_OVER ? "Turned over · " + condition : condition;
    }
}
