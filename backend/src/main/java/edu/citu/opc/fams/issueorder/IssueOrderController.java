package edu.citu.opc.fams.issueorder;

import edu.citu.opc.fams.asset.Asset;
import edu.citu.opc.fams.asset.AssetCustodyService;
import edu.citu.opc.fams.reference.Department;
import edu.citu.opc.fams.reference.DepartmentService;
import edu.citu.opc.fams.reference.Employee;
import edu.citu.opc.fams.reference.EmployeeService;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** SDD 3.2 / 3.3: /api/issue-orders. Administrators and Property Staff (SRS 2.2). */
@RestController
@RequestMapping("/api/issue-orders")
@PreAuthorize("hasAnyRole('ADMIN','STAFF')")
public class IssueOrderController {

    private final IssueOrderService service;
    private final IssueOrderPdf pdf;
    private final AssetCustodyService custody;
    private final DepartmentService departments;
    private final EmployeeService employees;

    public IssueOrderController(IssueOrderService service, IssueOrderPdf pdf, AssetCustodyService custody,
                                DepartmentService departments, EmployeeService employees) {
        this.service = service;
        this.pdf = pdf;
        this.custody = custody;
        this.departments = departments;
        this.employees = employees;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Detail prepare(@RequestBody IssueOrderService.PrepareRequest r) {
        return detail(service.prepare(r).getId());
    }

    @GetMapping("/next-number")
    public Map<String, String> nextNumber() {
        return Map.of("ioNo", service.previewNextNumber());
    }

    @GetMapping
    public PageOf<Row> list(@RequestParam(required = false) String ioNo,
                            @RequestParam(required = false) Long departmentId,
                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                            @RequestParam(defaultValue = "0") int page,
                            @RequestParam(defaultValue = "25") int size) {
        Page<IssueOrder> p = service.search(new IssueOrderService.Filter(ioNo, departmentId, from, to), page, size);
        List<Long> ids = p.getContent().stream().map(IssueOrder::getId).toList();
        Map<Long, Long> counts = service.lineCounts(ids);
        Set<Long> deptIds = new HashSet<>();
        p.forEach(o -> deptIds.add(o.getDepartmentId()));
        Map<Long, Department> d = departments.byIds(deptIds);
        List<Row> rows = p.getContent().stream().map(o -> new Row(o.getId(), o.getIoNo(),
                code(d.get(o.getDepartmentId())), o.getReceivedByName(), counts.getOrDefault(o.getId(), 0L),
                o.getIssuedOn())).toList();
        return new PageOf<>(rows, p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages());
    }

    @GetMapping("/{id}")
    public Detail get(@PathVariable Long id) {
        return detail(id);
    }

    /** UC-202 step 7 / UC-204: the printable issue order. Generated on request, so a reprint is always possible. */
    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id,
                                      @RequestParam(defaultValue = "false") boolean download) {
        IssueOrder io = service.get(id);
        List<IssueOrderLine> lines = service.linesOf(id);
        List<IssueOrderPdf.Photo> photos = new ArrayList<>();
        for (Asset a : custody.issuedUnder(lines.stream().map(IssueOrderLine::getId).toList())) {
            byte[] bytes = custody.photoBytes(a);
            if (bytes != null) {
                photos.add(new IssueOrderPdf.Photo(
                        (a.getPropertyTagNo() != null ? a.getPropertyTagNo() + " · " : "") + a.getDescription(), bytes));
            }
        }
        byte[] body = pdf.render(new IssueOrderPdf.Data(io, code(departments.get(io.getDepartmentId())), lines, photos));
        String name = "Issue-Order-" + io.getIoNo() + ".pdf";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        (download ? ContentDisposition.attachment() : ContentDisposition.inline()).filename(name).build().toString())
                .body(body);
    }

    private Detail detail(Long id) {
        IssueOrder io = service.get(id);
        List<IssueOrderLine> lines = service.linesOf(id);
        Map<Long, Long> assetByLine = new HashMap<>();
        for (Asset a : custody.issuedUnder(lines.stream().map(IssueOrderLine::getId).toList())) {
            assetByLine.putIfAbsent(a.getIssueOrderLineId(), a.getId());
        }
        Department d = departments.get(io.getDepartmentId());
        Employee e = io.getEmployeeId() == null ? null : employees.byIds(List.of(io.getEmployeeId())).get(io.getEmployeeId());
        BigDecimal total = lines.stream().map(IssueOrderLine::getTotalCost).filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Detail(io.getId(), io.getIoNo(), io.getIssuedOn(), io.getDepartmentId(), d.getCode(), d.getName(),
                io.getRoom(), io.getEmployeeId(), e == null ? io.getReceivedByName() : e.getFullName(),
                io.getIssuedByName(), io.getPurchasedFrom(), io.getInvoiceNo(), io.getInvoiceDate(), io.getRemarks(),
                io.getApprovedByName(), io.getNotedByName(), io.getNotedByTitle(), io.getReceivedByName(),
                io.getReceivedByTitle(), io.getRequestId(), total, io.getCreatedAt(),
                lines.stream().map(l -> new LineView(l.getId(), l.getQuantity(), l.getUnit(), l.getDescription(),
                        l.getUnitCost(), l.getTotalCost(), assetByLine.get(l.getId()))).toList());
    }

    private static String code(Department d) {
        return d == null ? null : d.getCode();
    }

    public record Row(Long id, String ioNo, String departmentCode, String accountable, long lines, LocalDate issuedOn) {
    }

    public record LineView(Long id, int quantity, String unit, String description, BigDecimal unitCost,
                           BigDecimal totalCost, Long assetId) {
    }

    public record Detail(Long id, String ioNo, LocalDate issuedOn, Long departmentId, String departmentCode,
                         String departmentName, String room, Long employeeId, String accountable, String issuedByName,
                         String purchasedFrom, String invoiceNo, LocalDate invoiceDate, String remarks,
                         String approvedByName, String notedByName, String notedByTitle, String receivedByName,
                         String receivedByTitle, Long requestId, BigDecimal total, OffsetDateTime createdAt,
                         List<LineView> lines) {
    }

    public record PageOf<T>(List<T> items, int page, int size, long total, int totalPages) {
    }
}
