package edu.citu.opc.fams.document;

import org.springframework.data.domain.Page;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
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
import java.time.OffsetDateTime;
import java.util.List;

/**
 * SDD 1.1 / 1.2: /api/documents. Administrators, Property Staff and NAS Assistants (SRS 2.2: "Upload
 * document, review OCR fields, encode assets as drafts"). Department Heads have no access.
 */
@RestController
@RequestMapping("/api/documents")
@PreAuthorize("hasAnyRole('ADMIN','STAFF','NAS')")
public class DocumentController {

    private final DocumentService service;

    public DocumentController(DocumentService service) {
        this.service = service;
    }

    /** UC-101: POST /api/documents (multipart: file, type, docNo, confirmDuplicate). */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentService.Detail upload(@RequestPart(value = "file", required = false) MultipartFile file,
                                         @RequestParam(required = false) String type,
                                         @RequestParam(required = false) String docNo,
                                         @RequestParam(defaultValue = "false") boolean confirmDuplicate) throws IOException {
        return service.detail(service.upload(file, type, docNo, confirmDuplicate).getId());
    }

    public record Row(Long id, String type, String typeLabel, String docNo, String poNo, String prNo, String supplier,
                      LocalDate docDate, String status, OffsetDateTime createdAt, Integer encodingSeconds) {
    }

    public record PageOf<T>(List<T> items, int page, int size, long total, int totalPages) {
    }

    @GetMapping
    public PageOf<Row> list(@RequestParam(required = false) String status,
                            @RequestParam(required = false) String type,
                            @RequestParam(required = false) String q,
                            @RequestParam(defaultValue = "0") int page,
                            @RequestParam(defaultValue = "25") int size) {
        Page<SourceDocument> p = service.search(status, type, q, page, size);
        List<Row> rows = p.getContent().stream().map(d -> new Row(d.getId(), d.getType().name(), d.getType().label(),
                d.getDocNo(), d.getPoNo(), d.getPrNo(), d.getSupplier(), d.getDocDate(), d.getStatus().name(),
                d.getCreatedAt(), d.getEncodingSeconds())).toList();
        return new PageOf<>(rows, p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages());
    }

    @GetMapping("/{id}")
    public DocumentService.Detail get(@PathVariable Long id) {
        return service.detail(id);
    }

    /** SDD 1.2: GET /api/documents/{id}/lines. */
    @GetMapping("/{id}/lines")
    public List<DocumentService.LineView> lines(@PathVariable Long id) {
        return service.lineViews(id);
    }

    /** The scanned file, for the preview beside the fields. */
    @GetMapping("/{id}/file")
    public ResponseEntity<byte[]> file(@PathVariable Long id) throws IOException {
        DocumentService.FileContent f = service.file(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(f.contentType()))
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(f.name()).build().toString())
                .body(f.bytes());
    }

    @PutMapping("/{id}")
    public DocumentService.Detail updateHeader(@PathVariable Long id, @RequestBody DocumentService.Header h) {
        service.updateHeader(id, h);
        return service.detail(id);
    }

    /** Reads the file again, e.g. after a failed OCR. Lines not yet confirmed are replaced. */
    @PostMapping("/{id}/read-again")
    public DocumentService.Detail readAgain(@PathVariable Long id) {
        service.readAgain(id);
        return service.detail(id);
    }

    /** SDD 1.2: POST /api/documents/{id}/confirm. */
    @PostMapping("/{id}/confirm")
    public DocumentService.Confirmed confirm(@PathVariable Long id, @RequestBody DocumentService.ConfirmRequest r) {
        return service.confirm(id, r);
    }
}
