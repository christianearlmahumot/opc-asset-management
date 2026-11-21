package edu.citu.opc.fams.asset;

import edu.citu.opc.fams.access.CurrentUser;
import edu.citu.opc.fams.access.Role;
import edu.citu.opc.fams.access.UserDirectory;
import edu.citu.opc.fams.audit.AuditAction;
import edu.citu.opc.fams.audit.AuditService;
import edu.citu.opc.fams.common.ApiException;
import edu.citu.opc.fams.common.storage.FileStorage;
import edu.citu.opc.fams.issueorder.IssueOrderLookup;
import edu.citu.opc.fams.movement.Movement;
import edu.citu.opc.fams.movement.MovementLedger;
import edu.citu.opc.fams.movement.MovementType;
import edu.citu.opc.fams.reference.CategoryService;
import edu.citu.opc.fams.reference.Department;
import edu.citu.opc.fams.reference.DepartmentService;
import edu.citu.opc.fams.reference.Employee;
import edu.citu.opc.fams.reference.EmployeeService;
import edu.citu.opc.fams.reference.UnitService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Module 2 Asset Register: Add (UC-104), Edit (UC-106), Search and History (UC-205).
 */
@Service
public class AssetService {

    private static final Set<String> PHOTO_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final long PHOTO_MAX_BYTES = 5L * 1024 * 1024;

    private final AssetRepository repo;
    private final DuplicateCheckService duplicates;
    private final MovementLedger ledger;
    private final AuditService audit;
    private final DepartmentService departments;
    private final EmployeeService employees;
    private final CategoryService categories;
    private final UnitService units;
    private final UserDirectory userDirectory;
    private final FileStorage storage;
    private final IssueOrderLookup issueOrders;

    public AssetService(AssetRepository repo, DuplicateCheckService duplicates, MovementLedger ledger,
                        AuditService audit, DepartmentService departments, EmployeeService employees,
                        CategoryService categories, UnitService units, UserDirectory userDirectory,
                        FileStorage storage, IssueOrderLookup issueOrders) {
        this.issueOrders = issueOrders;
        this.repo = repo;
        this.duplicates = duplicates;
        this.ledger = ledger;
        this.audit = audit;
        this.departments = departments;
        this.employees = employees;
        this.categories = categories;
        this.units = units;
        this.userDirectory = userDirectory;
        this.storage = storage;
    }

    // ------------------------------------------------------------------ search (UC-205)

    public record SearchFilter(String q, Long departmentId, AssetStatus status, AssetCondition condition,
                               boolean includeDrafts) {
    }

    @Transactional(readOnly = true)
    public Page<AssetViews.Row> search(SearchFilter f, int page, int size) {
        CurrentUser me = CurrentUser.get();
        Long scope = me.scopedDepartmentId();
        Long dept = scope != null ? scope : f.departmentId();

        List<Specification<Asset>> specs = new ArrayList<>();
        if (dept != null) specs.add((r, q, cb) -> cb.equal(r.get("departmentId"), dept));
        if (f.status() != null) {
            specs.add((r, q, cb) -> cb.equal(r.get("status"), f.status()));
        } else if (!f.includeDrafts() || me.is(Role.DEPT_HEAD)) {
            specs.add((r, q, cb) -> cb.notEqual(r.get("status"), AssetStatus.DRAFT));
        }
        if (f.status() == AssetStatus.DRAFT && me.is(Role.DEPT_HEAD)) {
            specs.add((r, q, cb) -> cb.disjunction());
        }
        if (f.condition() != null) specs.add((r, q, cb) -> cb.equal(r.get("condition"), f.condition()));
        if (f.q() != null && !f.q().isBlank()) {
            String like = "%" + f.q().trim().toLowerCase(Locale.ROOT) + "%";
            List<Long> holderIds = employees.idsByName(f.q());
            // UC-205: an issue order number (7 digits) finds every item released under it.
            List<Long> ioLineIds = f.q().trim().matches("\\d{7}") ? issueOrders.lineIdsForIoNo(f.q().trim()) : List.of();
            specs.add((r, q, cb) -> {
                var or = new ArrayList<jakarta.persistence.criteria.Predicate>();
                for (String field : List.of("description", "propertyTagNo", "serialNo", "brand", "modelNo",
                        "invoiceNo", "supplier", "room")) {
                    or.add(cb.like(cb.lower(r.get(field)), like));
                }
                if (!holderIds.isEmpty()) or.add(r.get("employeeId").in(holderIds));
                if (!ioLineIds.isEmpty()) or.add(r.get("issueOrderLineId").in(ioLineIds));
                return cb.or(or.toArray(jakarta.persistence.criteria.Predicate[]::new));
            });
        }

        Page<Asset> result = repo.findAll(Specification.allOf(specs),
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                        Sort.by(Sort.Direction.DESC, "updatedAt").and(Sort.by("id"))));

        Set<Long> deptIds = new HashSet<>();
        Set<Long> empIds = new HashSet<>();
        result.forEach(a -> {
            if (a.getDepartmentId() != null) deptIds.add(a.getDepartmentId());
            if (a.getEmployeeId() != null) empIds.add(a.getEmployeeId());
        });
        Map<Long, Department> deptMap = departments.byIds(deptIds);
        Map<Long, Employee> empMap = employees.byIds(empIds);

        return result.map(a -> new AssetViews.Row(a.getId(), a.getPropertyTagNo(), a.getDescription(), a.getSerialNo(),
                a.getQuantity(), a.getUnit(), a.getDepartmentId(),
                a.getDepartmentId() == null ? null : codeOf(deptMap.get(a.getDepartmentId())),
                a.getRoom(), a.getEmployeeId(),
                a.getEmployeeId() == null ? null : nameOf(empMap.get(a.getEmployeeId())),
                a.getCondition(), a.getCondition().label(), a.getStatus(), a.getStatus().label(),
                a.getUnitCost(), a.getSupplier(), a.getInvoiceNo(), a.getAcquisitionDate(), a.getPhotoPath() != null));
    }

    // ------------------------------------------------------------------ read one

    @Transactional(readOnly = true)
    public Asset getVisible(Long id) {
        Asset a = repo.findById(id).orElseThrow(() -> ApiException.notFound("Asset"));
        CurrentUser me = CurrentUser.get();
        me.checkCanSee(a.getDepartmentId(), "Asset");
        if (a.getStatus() == AssetStatus.DRAFT && me.is(Role.DEPT_HEAD)) throw ApiException.notFound("Asset");
        return a;
    }

    @Transactional(readOnly = true)
    public AssetViews.Detail detail(Long id) {
        return toDetail(getVisible(id));
    }

    AssetViews.Detail toDetail(Asset a) {
        Department d = a.getDepartmentId() == null ? null : departments.byIds(List.of(a.getDepartmentId())).get(a.getDepartmentId());
        Employee e = a.getEmployeeId() == null ? null : employees.byIds(List.of(a.getEmployeeId())).get(a.getEmployeeId());
        String createdBy = a.getCreatedBy() == null ? null
                : userDirectory.namesByIds(List.of(a.getCreatedBy())).get(a.getCreatedBy());
        BigDecimal total = a.getUnitCost() == null ? null : a.getUnitCost().multiply(BigDecimal.valueOf(a.getQuantity()));
        var io = issueOrders.orderOfLine(a.getIssueOrderLineId());
        return new AssetViews.Detail(a.getId(), a.getPropertyTagNo(), a.getDescription(), a.getCategoryId(),
                a.getCategoryId() == null ? null : categories.names().get(a.getCategoryId()),
                a.getBrand(), a.getModelNo(), a.getSerialNo(), a.getColor(), a.getDimension(),
                a.getQuantity(), a.getUnit(), a.getAcquisitionDate(), a.getUnitCost(), total,
                a.getSupplier(), a.getInvoiceNo(),
                a.getDepartmentId(), codeOf(d), d == null ? null : d.getName(), a.getRoom(),
                a.getEmployeeId(), nameOf(e),
                a.getCondition(), a.getCondition().label(), a.getStatus(), a.getStatus().label(),
                a.getPhotoPath() != null, a.getParentAssetId(), a.getCreatedBy(), createdBy, a.getCreatedAt(), a.getUpdatedAt(),
                io.map(IssueOrderLookup.Ref::id).orElse(null), io.map(IssueOrderLookup.Ref::ioNo).orElse(null));
    }

    // ------------------------------------------------------------------ add (UC-104)

    @Transactional
    public Asset create(AssetForm f) {
        CurrentUser me = CurrentUser.get();
        String tag = blankToNull(f.propertyTagNo());
        String unit = units.normalize(f.unit());
        checkReferences(f.categoryId(), f.departmentId(), f.employeeId());
        checkTagFree(tag, null);
        checkDuplicates(f, null);

        Asset a = new Asset(me.id());
        applyDescriptiveFields(a, f, tag, unit);
        a.placeInitially(f.departmentId(), blankToNull(f.room()), f.employeeId());
        a.setCondition(f.condition());
        // A NAS Assistant's entries stay as drafts until Property Staff confirm them (SRS 2.2).
        a.setStatus(me.is(Role.NAS) ? AssetStatus.DRAFT : activeStatusFor(a));
        repo.save(a);

        audit.record(AuditAction.CREATE, "asset", a.getId(), null, a.auditValues());
        if (a.getStatus() != AssetStatus.DRAFT) recordEncode(a);
        return a;
    }

    /** Property Staff confirm a NAS draft: it joins the register and its ENCODE movement is written. */
    @Transactional
    public Asset confirmDraft(Long id) {
        Asset a = repo.findById(id).orElseThrow(() -> ApiException.notFound("Asset"));
        if (a.getStatus() != AssetStatus.DRAFT) {
            throw ApiException.badRequest("This asset is not a draft.");
        }
        var before = a.auditValues();
        a.setStatus(activeStatusFor(a));
        audit.recordChanges("asset", a.getId(), before, a.auditValues());
        recordEncode(a);
        return a;
    }

    // ------------------------------------------------------------------ edit (UC-106)

    @Transactional
    public Asset update(Long id, AssetForm f) {
        CurrentUser me = CurrentUser.get();
        Asset a = repo.findById(id).orElseThrow(() -> ApiException.notFound("Asset"));
        checkNasMayChange(me, a);
        if (a.getStatus() == AssetStatus.DISPOSED) {
            throw ApiException.badRequest("A disposed asset cannot be edited.");
        }
        // UC-106 3a: custody changes are movements, not edits (drafts are not yet in custody, so they may change).
        if (a.getStatus() != AssetStatus.DRAFT
                && (!Objects.equals(f.departmentId(), a.getDepartmentId())
                || !Objects.equals(f.employeeId(), a.getEmployeeId())
                || !Objects.equals(blankToNull(f.room()), a.getRoom()))) {
            throw ApiException.badRequest(
                    "The department, room and accountable person change only by recording a transfer.");
        }

        String tag = blankToNull(f.propertyTagNo());
        String unit = units.normalize(f.unit());
        checkReferences(f.categoryId(), f.departmentId(), f.employeeId());
        if (!Objects.equals(lower(tag), lower(a.getPropertyTagNo()))) checkTagFree(tag, id);
        boolean identityChanged = !Objects.equals(lower(f.serialNo()), lower(a.getSerialNo()))
                || !Objects.equals(lower(f.description()), lower(a.getDescription()))
                || !Objects.equals(lower(f.brand()), lower(a.getBrand()))
                || !Objects.equals(lower(f.modelNo()), lower(a.getModelNo()))
                || !Objects.equals(f.categoryId(), a.getCategoryId());
        if (identityChanged) checkDuplicates(f, id);

        var before = a.auditValues();
        AssetCondition oldCondition = a.getCondition();
        applyDescriptiveFields(a, f, tag, unit);
        if (a.getStatus() == AssetStatus.DRAFT) {
            a.placeInitially(f.departmentId(), blankToNull(f.room()), f.employeeId());
        }
        a.setCondition(f.condition());
        audit.recordChanges("asset", a.getId(), before, a.auditValues());

        // A condition change is part of the asset's history (UI-205 shows "Condition · Good Condition").
        if (oldCondition != f.condition() && a.getStatus() != AssetStatus.DRAFT) {
            ledger.record(Movement.of(a.getId(), MovementType.CONDITION, a.getQuantity())
                    .from(a.getDepartmentId(), a.getEmployeeId(), a.getRoom())
                    .to(a.getDepartmentId(), a.getEmployeeId(), a.getRoom())
                    .conditionAfter(f.condition().name())
                    .note("Updated from " + oldCondition.label() + " on Edit Asset"));
        }
        return a;
    }

    // ------------------------------------------------------------------ department holdings (UC-304)

    /**
     * The items a department currently holds: issued to it, not drafts, not disposed. For the OPC
     * itself this also includes what sits in OPC storage and the turned-over items it keeps, which
     * have no department. Access is checked by the caller (Department Heads: own department only).
     */
    @Transactional(readOnly = true)
    public List<Asset> currentInDepartment(Long departmentId) {
        List<Asset> items = new ArrayList<>(repo.findByDepartmentIdAndStatusIn(departmentId, List.of(AssetStatus.ISSUED)));
        boolean isOpc = departments.findOpc().map(d -> d.getId().equals(departmentId)).orElse(false);
        if (isOpc) {
            items.addAll(repo.findByDepartmentIdIsNullAndStatusIn(List.of(AssetStatus.IN_STORAGE, AssetStatus.TURNED_OVER)));
        }
        return items;
    }

    // ------------------------------------------------------------------ history (UC-205)

    @Transactional(readOnly = true)
    public List<AssetViews.HistoryEntry> history(Long id) {
        getVisible(id);
        List<Movement> moves = new ArrayList<>(ledger.history(id));
        // Parts split off this item: show the move that created each one (its oldest movement).
        Map<Long, Asset> children = new java.util.HashMap<>();
        for (Asset child : repo.findByParentAssetId(id)) {
            List<Movement> ch = ledger.history(child.getId());
            if (!ch.isEmpty()) {
                moves.add(ch.get(ch.size() - 1));
                children.put(child.getId(), child);
            }
        }
        moves.sort(java.util.Comparator.comparing(Movement::getRecordedAt).thenComparing(Movement::getId).reversed());
        Set<Long> deptIds = new HashSet<>();
        Set<Long> empIds = new HashSet<>();
        Set<Long> userIds = new HashSet<>();
        for (Movement m : moves) {
            addIfNotNull(deptIds, m.getFromDepartmentId(), m.getToDepartmentId());
            addIfNotNull(empIds, m.getFromEmployeeId(), m.getToEmployeeId());
            addIfNotNull(userIds, m.getRecordedBy());
        }
        Map<Long, Department> d = departments.byIds(deptIds);
        Map<Long, Employee> e = employees.byIds(empIds);
        Map<Long, String> u = userDirectory.namesByIds(userIds);
        return moves.stream().map(m -> {
            Asset child = m.getAssetId().equals(id) ? null : children.get(m.getAssetId());
            String label = child == null ? m.getType().label() : "Split off";
            String summary = MovementText.summary(m, d, e);
            if (child != null) summary = m.getQuantity() + " " + child.getUnit() + " · " + m.getType().label() + " · " + summary;
            return new AssetViews.HistoryEntry(m.getId(), m.getRecordedAt(), m.getType().name(), label,
                    m.getQuantity(), summary, u.get(m.getRecordedBy()), child == null ? null : child.getId());
        }).toList();
    }

    // ------------------------------------------------------------------ photo

    @Transactional(rollbackFor = IOException.class)
    public void savePhoto(Long id, MultipartFile file) throws IOException {
        CurrentUser me = CurrentUser.get();
        Asset a = repo.findById(id).orElseThrow(() -> ApiException.notFound("Asset"));
        checkNasMayChange(me, a);
        if (file == null || file.isEmpty()) throw ApiException.badRequest("Choose a photo.");
        if (!PHOTO_TYPES.contains(file.getContentType())) {
            throw ApiException.badRequest("The photo must be a JPG, PNG or WebP image.");
        }
        if (file.getSize() > PHOTO_MAX_BYTES) throw ApiException.badRequest("The photo must be 5 MB or smaller.");

        String ext = switch (file.getContentType()) {
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> "jpg";
        };
        String old = a.getPhotoPath();
        String path = storage.save("assets/" + a.getId() + "/photo-" + UUID.randomUUID() + "." + ext,
                file.getBytes(), file.getContentType());
        var before = a.auditValues();
        a.setPhotoPath(path);
        audit.recordChanges("asset", a.getId(), before, a.auditValues());
        if (old != null) {
            try { storage.delete(old); } catch (IOException ignored) { /* an orphan file is harmless */ }
        }
    }

    public record Photo(byte[] bytes, String contentType) {
    }

    @Transactional(readOnly = true)
    public Photo loadPhoto(Long id) throws IOException {
        Asset a = getVisible(id);
        if (a.getPhotoPath() == null) throw ApiException.notFound("Photo");
        String p = a.getPhotoPath().toLowerCase(Locale.ROOT);
        String type = p.endsWith(".png") ? "image/png" : p.endsWith(".webp") ? "image/webp" : "image/jpeg";
        return new Photo(storage.load(a.getPhotoPath()), type);
    }

    // ------------------------------------------------------------------ helpers

    /** SRS 2.2: a NAS Assistant's entries stay drafts; they may change only the drafts they encoded themselves. */
    private static void checkNasMayChange(CurrentUser me, Asset a) {
        if (me.is(Role.NAS) && (a.getStatus() != AssetStatus.DRAFT || !Objects.equals(a.getCreatedBy(), me.id()))) {
            throw ApiException.forbidden("NAS Assistants can only change drafts they encoded themselves.");
        }
    }

    private void recordEncode(Asset a) {
        String note = a.getInvoiceNo() != null ? "Encoded from invoice " + a.getInvoiceNo() : "Encoded manually";
        ledger.record(Movement.of(a.getId(), MovementType.ENCODE, a.getQuantity())
                .to(a.getDepartmentId(), a.getEmployeeId(), a.getRoom())
                .conditionAfter(a.getCondition().name())
                .note(note));
    }

    /** Without a department the item sits in OPC storage; with one (or the OPC itself as holder) it is issued. */
    private AssetStatus activeStatusFor(Asset a) {
        return a.getDepartmentId() == null ? AssetStatus.IN_STORAGE : AssetStatus.ISSUED;
    }

    private void applyDescriptiveFields(Asset a, AssetForm f, String tag, String unit) {
        a.setPropertyTagNo(tag == null ? null : tag.toUpperCase(Locale.ROOT));
        a.setDescription(f.description().trim());
        a.setCategoryId(f.categoryId());
        a.setBrand(blankToNull(f.brand()));
        a.setModelNo(blankToNull(f.modelNo()));
        a.setSerialNo(blankToNull(f.serialNo()));
        a.setColor(blankToNull(f.color()));
        a.setDimension(blankToNull(f.dimension()));
        a.setQuantity(f.quantity());
        a.setUnit(unit);
        a.setAcquisitionDate(f.acquisitionDate());
        a.setUnitCost(f.unitCost());
        a.setSupplier(blankToNull(f.supplier()));
        a.setInvoiceNo(blankToNull(f.invoiceNo()));
    }

    private void checkReferences(Long categoryId, Long departmentId, Long employeeId) {
        if (!categories.exists(categoryId)) throw ApiException.field("categoryId", "Choose a valid category.");
        if (departmentId != null && !departments.exists(departmentId)) {
            throw ApiException.field("departmentId", "Choose a valid department.");
        }
        if (employeeId != null) {
            if (departmentId == null) {
                throw ApiException.field("departmentId", "Choose the department of the accountable person.");
            }
            Employee e = employees.get(employeeId);
            if (!e.isActive()) {
                throw ApiException.field("employeeId", e.getFullName() + " is no longer active.");
            }
            if (e.getDepartmentId() != null && !e.getDepartmentId().equals(departmentId)) {
                throw ApiException.field("employeeId", e.getFullName() + " belongs to another department.");
            }
        }
    }

    /** UC-104 5b / REQ-105: a property tag identifies exactly one asset. */
    private void checkTagFree(String tag, Long selfId) {
        if (tag == null) return;
        repo.findByPropertyTagNoIgnoreCase(tag).filter(o -> !o.getId().equals(selfId)).ifPresent(o -> {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Property tag " + o.getPropertyTagNo() + " is already used by: " + o.getDescription() + ".",
                    Map.of("propertyTagNo", "Already used by another asset."),
                    Map.of("duplicates", List.of(new DuplicateCheckService.Match(o.getId(), o.getPropertyTagNo(),
                            o.getDescription(), o.getSerialNo(), "property tag " + o.getPropertyTagNo())),
                            "canOverride", false));
        });
    }

    /** UC-104 5b: a likely duplicate is shown; the user cancels or confirms it is a different item. */
    private void checkDuplicates(AssetForm f, Long selfId) {
        if (f.confirmDuplicate()) return;
        var matches = duplicates.find(f.serialNo(), f.categoryId(), f.description(), f.brand(), f.modelNo(), selfId);
        if (!matches.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Possible duplicate: " + matches.get(0).reason() + " already exists.",
                    Map.of(), Map.of("duplicates", matches, "canOverride", true));
        }
    }

    private static void addIfNotNull(Set<Long> set, Long... ids) {
        for (Long id : ids) if (id != null) set.add(id);
    }

    private static String codeOf(Department d) {
        return d == null ? null : d.getCode();
    }

    private static String nameOf(Employee e) {
        return e == null ? null : e.getFullName();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String lower(String s) {
        String b = blankToNull(s);
        return b == null ? null : b.toLowerCase(Locale.ROOT);
    }
}
