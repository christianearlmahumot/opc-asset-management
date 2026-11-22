package edu.citu.opc.fams.report;

import edu.citu.opc.fams.asset.Asset;
import edu.citu.opc.fams.asset.AssetCondition;
import edu.citu.opc.fams.asset.AssetService;
import edu.citu.opc.fams.audit.AuditAction;
import edu.citu.opc.fams.audit.AuditService;
import edu.citu.opc.fams.common.ApiException;
import edu.citu.opc.fams.issueorder.IssueOrder;
import edu.citu.opc.fams.issueorder.IssueOrderLine;
import edu.citu.opc.fams.issueorder.IssueOrderService;
import edu.citu.opc.fams.movement.Movement;
import edu.citu.opc.fams.movement.MovementLedger;
import edu.citu.opc.fams.movement.TurnoverReason;
import edu.citu.opc.fams.reference.Department;
import edu.citu.opc.fams.reference.DepartmentService;
import edu.citu.opc.fams.reference.Employee;
import edu.citu.opc.fams.reference.EmployeeService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * UC-305 Export Reports (SDD 5.2 ExportService): the three reports the OPC already keeps, with the same
 * columns, as Excel (Apache POI) or PDF (OpenPDF). Every export is written to the audit log (EXPORT).
 *
 * <p>Not one transaction on purpose: each read runs in its own read-only transaction and the audit
 * entry is written in its own, after the file was built.
 */
@Service
public class ExportService {

    private static final ZoneId MANILA = ZoneId.of("Asia/Manila");
    private static final DateTimeFormatter LONG = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a", Locale.ENGLISH);

    public enum Type {
        MASTERLIST("Masterlist of Issue Orders", "Masterlist-of-Issue-Orders"),
        DEPARTMENT("Department Inventory Report", "Department-Inventory"),
        TURNED_OVER("Inventory of Turned-over Items", "Inventory-of-Turned-over-Items");

        final String title;
        final String fileName;

        Type(String title, String fileName) {
            this.title = title;
            this.fileName = fileName;
        }
    }

    public enum Format {
        XLSX("Excel", "xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
        PDF("PDF", "pdf", "application/pdf");

        final String label;
        final String extension;
        final String contentType;

        Format(String label, String extension, String contentType) {
            this.label = label;
            this.extension = extension;
            this.contentType = contentType;
        }
    }

    /** {@code departmentId} is required for the department report; the dates filter the other two. */
    public record Request(Type type, Format format, Long departmentId, LocalDate from, LocalDate to) {
    }

    public record File(String fileName, String contentType, byte[] bytes, int rows) {
    }

    private final IssueOrderService issueOrders;
    private final SummaryService summaries;
    private final MovementLedger ledger;
    private final AssetService assets;
    private final DepartmentService departments;
    private final EmployeeService employees;
    private final AuditService audit;
    private final String institution;

    public ExportService(IssueOrderService issueOrders, SummaryService summaries, MovementLedger ledger,
                         AssetService assets, DepartmentService departments, EmployeeService employees,
                         AuditService audit, @Value("${fams.issue-order.institution}") String institution) {
        this.issueOrders = issueOrders;
        this.summaries = summaries;
        this.ledger = ledger;
        this.assets = assets;
        this.departments = departments;
        this.employees = employees;
        this.audit = audit;
        this.institution = institution;
    }

    public File export(Request r) {
        if (r.type() == null) throw ApiException.field("type", "Choose the report.");
        if (r.format() == null) throw ApiException.field("format", "Choose Excel or PDF.");
        if (r.from() != null && r.to() != null && r.from().isAfter(r.to())) {
            throw ApiException.field("from", "The start date is after the end date.");
        }
        Department dept = null;
        if (r.departmentId() != null) dept = departments.get(r.departmentId());   // 404 if unknown
        if (r.type() == Type.DEPARTMENT && dept == null) {
            throw ApiException.field("departmentId", "Choose the department.");
        }

        ReportTable table = switch (r.type()) {
            case MASTERLIST -> masterlist(dept, r.from(), r.to());
            case DEPARTMENT -> departmentInventory(dept);
            case TURNED_OVER -> turnedOver(dept, r.from(), r.to());
        };

        LocalDateTime now = LocalDateTime.now(MANILA);
        byte[] bytes = r.format() == Format.XLSX
                ? ReportXlsx.write(table)
                : ReportPdf.write(table, institution, "Generated " + now.format(STAMP));

        StringBuilder name = new StringBuilder(r.type().fileName);
        if (dept != null) name.append('-').append(dept.getCode().replaceAll("[^A-Za-z0-9]+", ""));
        name.append('-').append(now.toLocalDate()).append('.').append(r.format().extension);

        Map<String, Object> v = new LinkedHashMap<>();
        v.put("report", r.type().title);
        v.put("format", r.format().label);
        v.put("department", dept == null ? null : dept.getCode());
        v.put("from", r.from() == null ? null : r.from().toString());
        v.put("to", r.to() == null ? null : r.to().toString());
        v.put("rows", table.itemRows());
        audit.record(AuditAction.EXPORT, "report", null, null, v);

        return new File(name.toString(), r.format().contentType, bytes, table.itemRows());
    }

    // ------------------------------------------------------------------ Masterlist of Issue Orders

    /** The 14 columns of the OPC's Excel masterlist (SRS UC-107 step 2), one row per issue order line. */
    private ReportTable masterlist(Department dept, LocalDate from, LocalDate to) {
        ReportTable t = new ReportTable(Type.MASTERLIST.title);
        t.sheetName = "Masterlist";
        t.excelHeadingRows = false;
        t.subtitle.add(filterLine(dept, from, to, "Issued"));
        t.column("Issue Order #", ReportTable.Kind.TEXT, 11)
                .column("Issued By", ReportTable.Kind.TEXT, 20)
                .column("Department", ReportTable.Kind.TEXT, 12)
                .column("Accountable Person", ReportTable.Kind.TEXT, 24)
                .column("Designation", ReportTable.Kind.TEXT, 22)
                .column("Invoice Date", ReportTable.Kind.DATE, 11)
                .column("Purchased From", ReportTable.Kind.TEXT, 24)
                .column("Invoice #", ReportTable.Kind.TEXT, 12)
                .column("Qty", ReportTable.Kind.INT, 6)
                .column("Unit", ReportTable.Kind.TEXT, 7)
                .column("Items/Description", ReportTable.Kind.TEXT, 48)
                .column("Acquisition Unit Cost", ReportTable.Kind.MONEY, 14)
                .column("Acquisition Total Cost", ReportTable.Kind.MONEY, 14)
                .column("Remarks", ReportTable.Kind.TEXT, 22);

        List<IssueOrderService.MasterlistLine> rows = issueOrders.masterlist(
                new IssueOrderService.Filter(null, dept == null ? null : dept.getId(), from, to));
        Map<Long, Department> depts = departments.byIds(
                rows.stream().map(x -> x.order().getDepartmentId()).collect(Collectors.toSet()));
        for (IssueOrderService.MasterlistLine x : rows) {
            IssueOrder o = x.order();
            IssueOrderLine l = x.line();
            Department d = depts.get(o.getDepartmentId());
            t.item(o.getIoNo(), o.getIssuedByName(), d == null ? null : d.getCode(), o.getReceivedByName(),
                    o.getReceivedByTitle(), o.getInvoiceDate(), o.getPurchasedFrom(), o.getInvoiceNo(),
                    l.getQuantity(), l.getUnit(), l.getDescription(), l.getUnitCost(), l.getTotalCost(),
                    o.getRemarks());
        }
        return t;
    }

    // ------------------------------------------------------------------ Department Inventory Report

    /** The same report as UC-304 on screen: items grouped by room, with subtotals and a grand total. */
    private ReportTable departmentInventory(Department dept) {
        SummaryService.DepartmentSummary s = summaries.departmentSummary(dept.getId());
        ReportTable t = new ReportTable(Type.DEPARTMENT.title);
        t.sheetName = "Inventory " + s.departmentCode();
        t.landscape = false;
        t.emptyText = "This department has no assets.";
        t.subtitle.add(s.departmentCode() + " · " + s.departmentName());
        t.subtitle.add("As of " + s.asOf().format(LONG));
        t.column("QTY", ReportTable.Kind.INT, 6)
                .column("UNIT", ReportTable.Kind.TEXT, 7)
                .column("DESCRIPTION", ReportTable.Kind.TEXT, 52)
                .column("YEAR", ReportTable.Kind.TEXT, 6)
                .column("UNIT COST", ReportTable.Kind.MONEY, 13)
                .column("TOTAL COST", ReportTable.Kind.MONEY, 14)
                .column("REMARKS", ReportTable.Kind.TEXT, 18);

        for (SummaryService.RoomGroup g : s.rooms()) {
            t.group(g.room());
            for (SummaryService.Line l : g.lines()) {
                t.item(l.quantity(), l.unit(), describe(l), l.year() == null ? null : String.valueOf(l.year()),
                        l.unitCost(), l.totalCost(), l.remarks());
            }
            t.subtotal(g.totalQuantity(), null, "Subtotal · " + g.room(), null, null, g.totalCost(), null);
        }
        if (!s.rooms().isEmpty()) {
            t.total(s.totalQuantity(), null,
                    "GRAND TOTAL · " + s.itemCount() + (s.itemCount() == 1 ? " item" : " items"),
                    null, null, s.totalCost(), null);
        }
        if (s.itemsWithoutCost() > 0) {
            t.notes.add(s.itemsWithoutCost() + (s.itemsWithoutCost() == 1 ? " item has" : " items have")
                    + " no acquisition cost recorded; the totals leave them out.");
        }
        return t;
    }

    /** The description with the tag, brand and model, and serial number, as on the printed report. */
    private static String describe(SummaryService.Line l) {
        String brandModel = Stream.of(l.brand(), l.modelNo()).filter(ExportService::present)
                .collect(Collectors.joining(" "));
        String details = Stream.of(l.propertyTagNo(), brandModel, present(l.serialNo()) ? "S/N " + l.serialNo() : null)
                .filter(ExportService::present).collect(Collectors.joining(" · "));
        return details.isEmpty() ? l.description() : l.description() + "\n" + details;
    }

    // ------------------------------------------------------------------ Inventory of Turned-over Items

    /**
     * One row per item returned to OPC storage (UC-302 turnover or UC-303 resignation turnover), with the
     * standard reason instead of free text and the disposal date once the item is disposed of.
     */
    private ReportTable turnedOver(Department dept, LocalDate from, LocalDate to) {
        ReportTable t = new ReportTable(Type.TURNED_OVER.title);
        t.sheetName = "Turned-over Items";
        t.subtitle.add(filterLine(dept, from, to, "Returned"));
        t.column("Date Returned", ReportTable.Kind.DATE, 11)
                .column("Department", ReportTable.Kind.TEXT, 11)
                .column("Accountable Person", ReportTable.Kind.TEXT, 20)
                .column("Turned Over By", ReportTable.Kind.TEXT, 18)
                .column("Property Tag No.", ReportTable.Kind.TEXT, 14)
                .column("Qty", ReportTable.Kind.INT, 6)
                .column("Unit", ReportTable.Kind.TEXT, 7)
                .column("Description", ReportTable.Kind.TEXT, 36)
                .column("Brand / Model", ReportTable.Kind.TEXT, 18)
                .column("Serial No.", ReportTable.Kind.TEXT, 14)
                .column("Unit Cost", ReportTable.Kind.MONEY, 12)
                .column("Reason", ReportTable.Kind.TEXT, 14)
                .column("Condition", ReportTable.Kind.TEXT, 13)
                .column("Remarks", ReportTable.Kind.TEXT, 22)
                .column("Disposal Date", ReportTable.Kind.DATE, 11);

        List<Movement> moves = ledger.turnovers().stream()
                .filter(m -> dept == null || Objects.equals(m.getFromDepartmentId(), dept.getId()))
                .filter(m -> from == null || (m.getDateReturned() != null && !m.getDateReturned().isBefore(from)))
                .filter(m -> to == null || (m.getDateReturned() != null && !m.getDateReturned().isAfter(to)))
                .toList();

        Set<Long> assetIds = moves.stream().map(Movement::getAssetId).collect(Collectors.toSet());
        Set<Long> deptIds = new HashSet<>();
        Set<Long> empIds = new HashSet<>();
        for (Movement m : moves) {
            if (m.getFromDepartmentId() != null) deptIds.add(m.getFromDepartmentId());
            if (m.getFromEmployeeId() != null) empIds.add(m.getFromEmployeeId());
        }
        Map<Long, Asset> a = assetIds.isEmpty() ? Map.of() : assets.byIds(assetIds);
        Map<Long, Department> d = deptIds.isEmpty() ? Map.of() : departments.byIds(deptIds);
        Map<Long, Employee> e = employees.byIds(empIds);
        Map<Long, LocalDate> disposed = assetIds.isEmpty() ? Map.of() : ledger.disposalDates(assetIds);

        for (Movement m : moves) {
            Asset x = a.get(m.getAssetId());
            if (x == null) continue;
            Department from_ = d.get(m.getFromDepartmentId());
            Employee holder = e.get(m.getFromEmployeeId());
            String brandModel = Stream.of(x.getBrand(), x.getModelNo()).filter(ExportService::present)
                    .collect(Collectors.joining(" "));
            String turnedOverBy = m.getTurnedOverBy() != null ? m.getTurnedOverBy()
                    : holder != null ? holder.getFullName() : null;   // resignation: the person who left
            t.item(m.getDateReturned(), from_ == null ? null : from_.getCode(),
                    holder == null ? null : holder.getFullName(), turnedOverBy, x.getPropertyTagNo(),
                    m.getQuantity(), x.getUnit(), x.getDescription(), brandModel.isEmpty() ? null : brandModel,
                    x.getSerialNo(), x.getUnitCost(), reasonLabel(m), conditionLabel(m.getConditionAfter()),
                    m.getNote(), disposed.get(x.getId()));
        }
        return t;
    }

    private static String reasonLabel(Movement m) {
        if (m.getReason() == null) return m.getType().label();   // e.g. a usable item back from a resignation
        try {
            return TurnoverReason.valueOf(m.getReason()).label();
        } catch (IllegalArgumentException ex) {
            return m.getReason();
        }
    }

    private static String conditionLabel(String c) {
        if (c == null) return null;
        try {
            return AssetCondition.valueOf(c).label();
        } catch (IllegalArgumentException ex) {
            return c;
        }
    }

    // ------------------------------------------------------------------ helpers

    private static String filterLine(Department dept, LocalDate from, LocalDate to, String dateWord) {
        String who = dept == null ? "All departments" : dept.getCode() + " · " + dept.getName();
        String when;
        if (from != null && to != null) when = dateWord + " " + from.format(LONG) + " to " + to.format(LONG);
        else if (from != null) when = dateWord + " from " + from.format(LONG);
        else if (to != null) when = dateWord + " up to " + to.format(LONG);
        else when = "All dates";
        return who + " · " + when;
    }

    private static boolean present(String s) {
        return s != null && !s.isBlank();
    }
}
