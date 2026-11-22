package edu.citu.opc.fams.document;

import edu.citu.opc.fams.access.CurrentUser;
import edu.citu.opc.fams.access.Role;
import edu.citu.opc.fams.asset.Asset;
import edu.citu.opc.fams.asset.AssetCondition;
import edu.citu.opc.fams.asset.AssetForm;
import edu.citu.opc.fams.asset.AssetService;
import edu.citu.opc.fams.asset.DuplicateCheckService;
import edu.citu.opc.fams.audit.AuditAction;
import edu.citu.opc.fams.audit.AuditService;
import edu.citu.opc.fams.common.ApiException;
import edu.citu.opc.fams.common.storage.FileStorage;
import edu.citu.opc.fams.reference.ValueMappingService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * UC-101 Upload Purchasing Document and UC-102 Review and Confirm Extracted Fields (SDD DocumentService).
 * Property Staff, Administrators and NAS Assistants; a NAS Assistant's items are saved as drafts.
 */
@Service
public class DocumentService {

    private static final long MAX_BYTES = 15L * 1024 * 1024;

    private final SourceDocumentRepository documents;
    private final ExtractedLineRepository lines;
    private final FileStorage storage;
    private final OcrService ocr;
    private final PurchaseLookup purchases;
    private final AssetService assets;
    private final DuplicateCheckService duplicates;
    private final ValueMappingService mappings;
    private final AuditService audit;
    private final Validator validator;

    public DocumentService(SourceDocumentRepository documents, ExtractedLineRepository lines, FileStorage storage,
                           OcrService ocr, PurchaseLookup purchases, AssetService assets, DuplicateCheckService duplicates,
                           ValueMappingService mappings, AuditService audit, Validator validator) {
        this.documents = documents;
        this.lines = lines;
        this.storage = storage;
        this.ocr = ocr;
        this.purchases = purchases;
        this.assets = assets;
        this.duplicates = duplicates;
        this.mappings = mappings;
        this.audit = audit;
        this.validator = validator;
    }

    // ------------------------------------------------------------------ UC-101 upload

    @Transactional
    public SourceDocument upload(MultipartFile file, String type, String docNo, boolean confirmDuplicate) throws IOException {
        CurrentUser me = CurrentUser.get();
        SourceDocument.Type t = parseType(type);
        String no = docNo == null ? "" : docNo.trim();
        if (no.isEmpty()) throw ApiException.field("docNo", "Enter the document number.");
        if (no.length() > 40) throw ApiException.field("docNo", "Use at most 40 characters.");
        if (file == null || file.isEmpty()) throw ApiException.field("file", "Choose the scanned file.");
        if (file.getSize() > MAX_BYTES) throw ApiException.field("file", "The file is larger than 15 MB. Scan at 300 DPI or less.");
        byte[] bytes = file.getBytes();
        String contentType = sniff(bytes);
        if (contentType == null) {   // UC-101 4a
            throw ApiException.field("file", "Upload a JPG, PNG or PDF file.");
        }

        // UC-101 4b: the number already exists for this type of document.
        List<SourceDocument> same = documents.findSame(t, no);
        if (!same.isEmpty() && !confirmDuplicate) {
            throw new ApiException(HttpStatus.CONFLICT,
                    t.label() + " " + no + " was already uploaded. Upload it again only if this is a different document.",
                    Map.of(), Map.of("duplicates", same.stream().map(PurchaseLookup::view).toList()));
        }

        String ext = switch (contentType) {
            case "application/pdf" -> "pdf";
            case "image/png" -> "png";
            default -> "jpg";
        };
        String path = storage.save("documents/" + t.name().toLowerCase(Locale.ROOT) + "/" + UUID.randomUUID() + "." + ext,
                bytes, contentType);
        String name = file.getOriginalFilename() == null ? "document." + ext : file.getOriginalFilename();
        SourceDocument d = new SourceDocument(t, no, path, name.length() > 255 ? name.substring(0, 255) : name, contentType, me.id());
        // A PO's number is its PO number; a PR's number is its PR number. That is what links them.
        d.header(no, t == SourceDocument.Type.PURCHASE_ORDER ? SourceDocument.cut(no, 20) : null,
                t == SourceDocument.Type.PURCHASE_REQUEST ? no.toUpperCase(Locale.ROOT) : null, null, null, null);
        documents.save(d);
        audit.record(AuditAction.CREATE, "source_document", d.getId(), null, d.auditValues());

        Long id = d.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                ocr.queue(id);
            }
        });
        return d;
    }

    /** By content, not by name or browser-reported type: %PDF, PNG and JPEG signatures. */
    static String sniff(byte[] b) {
        if (b.length >= 4 && b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F') return "application/pdf";
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') return "image/png";
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) return "image/jpeg";
        return null;
    }

    private static SourceDocument.Type parseType(String type) {
        try {
            return SourceDocument.Type.valueOf(type == null ? "" : type.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw ApiException.field("type", "Choose sales invoice, purchase order or purchase request.");
        }
    }

    // ------------------------------------------------------------------ read

    public record Detail(Long id, String type, String typeLabel, String docNo, String poNo, String prNo,
                         String departmentCode, String supplier, LocalDate docDate, String status,
                         String originalName, String contentType, Integer pageCount, String ocrError,
                         Map<String, Integer> headerConfidence, Integer ocrSeconds, Integer encodingSeconds,
                         java.time.OffsetDateTime createdAt, java.time.OffsetDateTime encodedAt,
                         List<PurchaseLookup.Linked> linked, List<LineView> lines, List<AssetLink> assets,
                         boolean ocrAvailable) {
    }

    public record LineView(Long id, int lineNo, Integer quantity, String unit, String description, BigDecimal unitCost,
                           BigDecimal totalCost, String category, Map<String, Integer> confidence, boolean confirmed,
                           Long assetId) {
    }

    public record AssetLink(Long id, String description, int quantity, String status) {
    }

    @Transactional(readOnly = true)
    public Detail detail(Long id) {
        SourceDocument d = get(id);
        List<PurchaseLookup.Linked> linked = purchases.linked(d.getPoNo(), d.getPrNo()).stream()
                .filter(l -> !l.id().equals(d.getId())).toList();
        List<LineView> lv = lineViews(id);
        List<AssetLink> made = new ArrayList<>();
        for (Asset a : assets.fromDocument(id)) {
            made.add(new AssetLink(a.getId(), a.getDescription(), a.getQuantity(), a.getStatus().label()));
        }
        return new Detail(d.getId(), d.getType().name(), d.getType().label(), d.getDocNo(), d.getPoNo(), d.getPrNo(),
                d.getDepartmentCode(), d.getSupplier(), d.getDocDate(), d.getStatus().name(), d.getOriginalName(),
                d.getContentType(), d.getPageCount(), d.getOcrError(), d.getHeaderConfidence(), d.getOcrSeconds(),
                d.getEncodingSeconds(), d.getCreatedAt(), d.getEncodedAt(), linked, lv, made, ocr.available());
    }

    @Transactional(readOnly = true)
    public List<LineView> lineViews(Long id) {
        get(id);
        return lines.findByDocumentIdOrderByLineNoAsc(id).stream().map(l -> new LineView(l.getId(), l.getLineNo(),
                l.getQuantity(), l.getUnit(), l.getDescription(), l.getUnitCost(), l.getTotalCost(), l.getCategory(),
                l.getConfidences(), l.isConfirmed(), l.getAssetId())).toList();
    }

    @Transactional(readOnly = true)
    public Page<SourceDocument> search(String status, String type, String q, int page, int size) {
        SourceDocument.Status st = null;
        if (status != null && !status.isBlank()) {
            try {
                st = SourceDocument.Status.valueOf(status.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw ApiException.field("status", "Unknown status.");
            }
        }
        SourceDocument.Type ty = type == null || type.isBlank() ? null : parseType(type);
        String like = q == null || q.isBlank() ? null : "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
        return documents.search(st, ty, like, PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100)));
    }

    public record FileContent(byte[] bytes, String contentType, String name) {
    }

    @Transactional(readOnly = true)
    public FileContent file(Long id) throws IOException {
        SourceDocument d = get(id);
        return new FileContent(storage.load(d.getFilePath()), d.getContentType(), d.getOriginalName());
    }

    SourceDocument get(Long id) {
        return documents.findById(id).orElseThrow(() -> ApiException.notFound("Document"));
    }

    // ------------------------------------------------------------------ header edits and reading again

    public record Header(String docNo, String poNo, String prNo, String departmentCode, String supplier, LocalDate docDate) {
    }

    @Transactional
    public SourceDocument updateHeader(Long id, Header h) {
        SourceDocument d = get(id);
        if (d.getStatus() == SourceDocument.Status.ENCODED) throw ApiException.conflict("This document is already encoded.");
        applyHeader(d, h);
        return d;
    }

    private void applyHeader(SourceDocument d, Header h) {
        if (h == null) return;
        String no = h.docNo() == null || h.docNo().isBlank() ? d.getDocNo() : h.docNo().trim();
        Map<String, String> errors = new LinkedHashMap<>();
        if (no.length() > 40) errors.put("docNo", "Use at most 40 characters.");
        if (h.poNo() != null && h.poNo().trim().length() > 20) errors.put("poNo", "Use at most 20 characters.");
        if (h.prNo() != null && h.prNo().trim().length() > 40) errors.put("prNo", "Use at most 40 characters.");
        if (h.departmentCode() != null && h.departmentCode().trim().length() > 20) errors.put("departmentCode", "Use at most 20 characters.");
        if (h.supplier() != null && h.supplier().trim().length() > 120) errors.put("supplier", "Use at most 120 characters.");
        if (h.docDate() != null && h.docDate().isAfter(LocalDate.now())) errors.put("docDate", "The date cannot be in the future.");
        if (!errors.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "Check the highlighted fields.", errors, null);
        var before = d.auditValues();
        d.header(no, SourceDocument.cut(h.poNo(), 20), SourceDocument.cut(h.prNo(), 40),
                SourceDocument.cut(h.departmentCode() == null ? null : h.departmentCode().toUpperCase(Locale.ROOT), 20),
                SourceDocument.cut(h.supplier(), 120), h.docDate());
        audit.recordChanges("source_document", d.getId(), before, d.auditValues());
    }

    @Transactional
    public void readAgain(Long id) {
        SourceDocument d = get(id);
        if (d.getStatus() == SourceDocument.Status.ENCODED) throw ApiException.conflict("This document is already encoded.");
        if (d.getStatus() == SourceDocument.Status.PROCESSING || d.getStatus() == SourceDocument.Status.QUEUED) {
            throw ApiException.conflict("The document is already being read.");
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                ocr.queue(id);
            }
        });
    }

    // ------------------------------------------------------------------ UC-102 confirm

    public record ConfirmLine(Long lineId, Integer quantity, String unit, String description, BigDecimal unitCost,
                              Long categoryId, String propertyTagNo, String brand, String modelNo, String serialNo,
                              String color, String dimension) {
    }

    public record ConfirmRequest(Header header, List<ConfirmLine> lines, boolean confirmDuplicates) {
    }

    public record Confirmed(int saved, boolean drafts, List<Long> assetIds) {
    }

    /** UC-102 steps 5-7: validates every line, runs the duplicate check, then saves all items in one transaction. */
    @Transactional
    public Confirmed confirm(Long id, ConfirmRequest r) {
        CurrentUser me = CurrentUser.get();
        SourceDocument d = get(id);
        if (d.getStatus() == SourceDocument.Status.ENCODED) throw ApiException.conflict("This document is already encoded.");
        if (d.getStatus() == SourceDocument.Status.QUEUED || d.getStatus() == SourceDocument.Status.PROCESSING) {
            throw ApiException.conflict("Wait until the document has been read.");
        }
        if (r == null || r.lines() == null || r.lines().isEmpty()) throw ApiException.field("lines", "Add at least one item line.");
        applyHeader(d, r.header());

        Map<String, String> errors = new LinkedHashMap<>();
        if (d.getDocDate() == null) errors.put("docDate", "Enter the document date. It is the items' acquisition date.");
        Map<String, String> units = mappings.unitLookup();
        Set<String> serials = new HashSet<>();
        List<AssetForm> forms = new ArrayList<>();
        for (int i = 0; i < r.lines().size(); i++) {
            ConfirmLine l = r.lines().get(i);
            String unit = l.unit() == null || l.unit().isBlank() ? null : units.get(ValueMappingService.key(l.unit()));
            if (l.unit() != null && !l.unit().isBlank() && unit == null) {
                errors.put("lines." + i + ".unit", "The unit '" + l.unit().trim() + "' is not in the list. An Administrator can add it.");
            }
            boolean serial = l.serialNo() != null && !l.serialNo().isBlank();
            if (serial && l.quantity() != null && l.quantity() > 1) {
                errors.put("lines." + i + ".serialNo", "A serial number belongs to one item. Split the line or set the quantity to 1.");
            }
            if (serial && !serials.add(l.serialNo().trim().toLowerCase(Locale.ROOT))) {
                errors.put("lines." + i + ".serialNo", "This serial number is on another line too.");
            }
            AssetForm f = new AssetForm(l.description() == null ? null : l.description().trim(), l.categoryId(),
                    blank(l.propertyTagNo()), blank(l.brand()), blank(l.modelNo()), blank(l.serialNo()), blank(l.color()),
                    blank(l.dimension()), l.quantity(), unit == null ? l.unit() : unit, d.getDocDate(), l.unitCost(),
                    d.getSupplier(), d.getType() == SourceDocument.Type.SALES_INVOICE ? d.getDocNo() : null,
                    null, null, null, AssetCondition.GOOD, true);
            for (ConstraintViolation<AssetForm> v : validator.validate(f)) {
                String field = v.getPropertyPath().toString();
                if (field.equals("acquisitionDate")) errors.putIfAbsent("docDate", v.getMessage());
                else if (field.equals("supplier") || field.equals("invoiceNo")) errors.putIfAbsent(field, v.getMessage());
                else errors.putIfAbsent("lines." + i + "." + field, v.getMessage());
            }
            forms.add(f);
        }
        if (!errors.isEmpty()) {   // UC-102 6a
            throw new ApiException(HttpStatus.BAD_REQUEST, "Check the highlighted fields.", errors, null);
        }

        // UC-102 6b: lines that match an existing asset.
        if (!r.confirmDuplicates()) {
            List<Map<String, Object>> found = new ArrayList<>();
            for (int i = 0; i < forms.size(); i++) {
                AssetForm f = forms.get(i);
                var matches = duplicates.find(f.serialNo(), f.categoryId(), f.description(), f.brand(), f.modelNo(), null);
                if (!matches.isEmpty()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("line", i);
                    m.put("matches", matches);
                    found.add(m);
                }
            }
            if (!found.isEmpty()) {
                throw new ApiException(HttpStatus.CONFLICT,
                        found.size() == 1 ? "One line matches an existing asset." : found.size() + " lines match existing assets.",
                        Map.of(), Map.of("duplicates", found));
            }
        }

        Map<Long, ExtractedLine> byId = new HashMap<>();
        for (ExtractedLine el : lines.findByDocumentIdOrderByLineNoAsc(id)) byId.put(el.getId(), el);
        List<Long> ids = new ArrayList<>();
        int nextNo = byId.values().stream().mapToInt(ExtractedLine::getLineNo).max().orElse(0) + 1;
        for (int i = 0; i < forms.size(); i++) {
            Asset a = assets.create(forms.get(i), id);
            ids.add(a.getId());
            ConfirmLine l = r.lines().get(i);
            ExtractedLine el = l.lineId() == null ? null : byId.get(l.lineId());
            if (el == null) {   // a line the user added by hand
                el = lines.save(new ExtractedLine(id, nextNo++, new DocumentParser.Item(l.quantity(), forms.get(i).unit(),
                        l.description(), l.unitCost(), null, null, Map.of())));
            }
            el.confirmedAs(a.getId());
        }
        var before = d.auditValues();
        d.encoded(me.id());
        Map<String, Object> after = d.auditValues();
        after.put("assets", ids.size());
        after.put("encodingSeconds", d.getEncodingSeconds());
        audit.record(AuditAction.UPDATE, "source_document", d.getId(), before, after);
        return new Confirmed(ids.size(), me.is(Role.NAS), ids);
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
