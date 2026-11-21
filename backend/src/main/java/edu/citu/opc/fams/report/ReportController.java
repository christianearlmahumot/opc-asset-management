package edu.citu.opc.fams.report;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * SDD Module 5 endpoints. The department inventory report is for Administrators, Property Staff and
 * Department Heads (own department only, checked in SummaryService). NAS Assistants have no access
 * (SRS 2.2).
 */
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final SummaryService summaries;

    public ReportController(SummaryService summaries) {
        this.summaries = summaries;
    }

    /** SDD 5.1: GET /api/reports/departments/{id}/summary. */
    @GetMapping("/departments/{id}/summary")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF','DEPT_HEAD')")
    public SummaryService.DepartmentSummary departmentSummary(@PathVariable Long id) {
        return summaries.departmentSummary(id);
    }
}
