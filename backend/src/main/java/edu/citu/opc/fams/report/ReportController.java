package edu.citu.opc.fams.report;

import edu.citu.opc.fams.common.ApiException;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Locale;

/**
 * SDD Module 5 endpoints. The department inventory report is for Administrators, Property Staff and
 * Department Heads (own department only, checked in SummaryService). Exports are for Administrators
 * and Property Staff (UC-305). NAS Assistants have no access (SRS 2.2).
 */
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final SummaryService summaries;
    private final ExportService exports;

    public ReportController(SummaryService summaries, ExportService exports) {
        this.summaries = summaries;
        this.exports = exports;
    }

    /** SDD 5.1: GET /api/reports/departments/{id}/summary. */
    @GetMapping("/departments/{id}/summary")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF','DEPT_HEAD')")
    public SummaryService.DepartmentSummary departmentSummary(@PathVariable Long id) {
        return summaries.departmentSummary(id);
    }

    /**
     * SDD 5.2: GET /api/reports/export?type=&format=. type is MASTERLIST, DEPARTMENT or TURNED_OVER;
     * format is XLSX or PDF. The X-Export-Rows header tells the page how many rows the file has, so it
     * can say when the file holds headers only (UC-305 3a).
     */
    @GetMapping("/export")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) String type,
                                         @RequestParam(required = false) String format,
                                         @RequestParam(required = false) Long departmentId,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        ExportService.File f = exports.export(new ExportService.Request(
                parse(ExportService.Type.class, type, "type", "Choose the report."),
                parse(ExportService.Format.class, format, "format", "Choose Excel or PDF."),
                departmentId, from, to));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(f.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(f.fileName()).build().toString())
                .header("X-Export-Rows", String.valueOf(f.rows()))
                .body(f.bytes());
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String value, String field, String message) {
        if (value == null || value.isBlank()) throw ApiException.field(field, message);
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw ApiException.field(field, message);
        }
    }
}
