package edu.citu.opc.fams.asset;

import edu.citu.opc.fams.reference.DepartmentService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

/**
 * SDD Module 2 endpoints. Role rules follow SRS 2.2:
 * search/view - everyone (Department Heads scoped to their department);
 * add - Admin, Staff, NAS (NAS as drafts); edit - Admin, Staff (NAS: own drafts); confirm - Admin, Staff.
 */
@RestController
@RequestMapping("/api/assets")
public class AssetController {

    private final AssetService service;
    private final TagSuggester tags;
    private final DepartmentService departments;

    public AssetController(AssetService service, TagSuggester tags, DepartmentService departments) {
        this.service = service;
        this.tags = tags;
        this.departments = departments;
    }

    @GetMapping
    public PageResult<AssetViews.Row> search(@RequestParam(required = false) String q,
                                             @RequestParam(required = false) Long departmentId,
                                             @RequestParam(required = false) AssetStatus status,
                                             @RequestParam(required = false) AssetCondition condition,
                                             @RequestParam(defaultValue = "false") boolean includeDrafts,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "25") int size) {
        return PageResult.of(service.search(
                new AssetService.SearchFilter(q, departmentId, status, condition, includeDrafts), page, size));
    }

    @GetMapping("/{id}")
    public AssetViews.Detail get(@PathVariable Long id) {
        return service.detail(id);
    }

    @GetMapping("/{id}/history")
    public List<AssetViews.HistoryEntry> history(@PathVariable Long id) {
        return service.history(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','STAFF','NAS')")
    @ResponseStatus(HttpStatus.CREATED)
    public AssetViews.Detail create(@Valid @RequestBody AssetForm form) {
        return service.toDetail(service.create(form));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF','NAS')")
    public AssetViews.Detail update(@PathVariable Long id, @Valid @RequestBody AssetForm form) {
        return service.toDetail(service.update(id, form));
    }

    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    public AssetViews.Detail confirm(@PathVariable Long id) {
        return service.toDetail(service.confirmDraft(id));
    }

    @PostMapping(path = "/{id}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','STAFF','NAS')")
    public AssetViews.Detail uploadPhoto(@PathVariable Long id, @RequestPart("file") MultipartFile file)
            throws IOException {
        service.savePhoto(id, file);
        return service.detail(id);
    }

    @GetMapping("/{id}/photo")
    public ResponseEntity<byte[]> photo(@PathVariable Long id) throws IOException {
        var p = service.loadPhoto(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(p.contentType()))
                .cacheControl(CacheControl.noStore())
                .body(p.bytes());
    }

    /** UC-104 step 3: the next free tag for year-office-item type, e.g. 2026-MEDTECH-CNTR-03. */
    @GetMapping("/tag-suggestion")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF','NAS')")
    public TagSuggester.Suggestion suggestTag(@RequestParam(required = false) Integer year,
                                              @RequestParam(required = false) Long departmentId,
                                              @RequestParam(required = false) String description,
                                              @RequestParam(required = false) String itemType) {
        String office = departmentId == null ? "OPC" : departments.get(departmentId).getCode();
        int y = year != null ? year : LocalDate.now().getYear();
        return tags.suggest(y, office, description, itemType);
    }

    /** Small page wrapper so the JSON shape does not depend on Spring's Page serialization. */
    public record PageResult<T>(List<T> items, int page, int size, long total, int totalPages) {
        static <T> PageResult<T> of(Page<T> p) {
            return new PageResult<>(p.getContent(), p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages());
        }
    }
}
