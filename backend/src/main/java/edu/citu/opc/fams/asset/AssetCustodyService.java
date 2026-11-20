package edu.citu.opc.fams.asset;

import edu.citu.opc.fams.access.CurrentUser;
import edu.citu.opc.fams.audit.AuditAction;
import edu.citu.opc.fams.audit.AuditService;
import edu.citu.opc.fams.common.ApiException;
import edu.citu.opc.fams.common.storage.FileStorage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The only way to change an asset's department, room, accountable person or status after encoding.
 * Every method runs inside the caller's movement transaction (MANDATORY), so the asset change and its
 * movement row commit or roll back together (REQ-306).
 */
@Service
public class AssetCustodyService {

    private final AssetRepository repo;
    private final AuditService audit;
    private final FileStorage storage;

    public AssetCustodyService(AssetRepository repo, AuditService audit, FileStorage storage) {
        this.repo = repo;
        this.audit = audit;
        this.storage = storage;
    }

    /** Loads and row-locks the asset for the rest of the transaction. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Asset lock(Long id) {
        return repo.findForUpdate(id).orElseThrow(() -> ApiException.notFound("Asset"));
    }

    /** Splits qty off as a new asset with the same custody; returns the new asset. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Asset split(Asset original, int qty) {
        if (qty < 1 || qty >= original.getQuantity()) {
            throw ApiException.field("quantity", "Enter a quantity from 1 to " + (original.getQuantity() - 1) + ".");
        }
        var before = original.auditValues();
        Asset part = original.splitOff(qty, CurrentUser.get().id());
        repo.save(part);
        copyPhoto(original, part);
        audit.recordChanges("asset", original.getId(), before, original.auditValues());
        audit.record(AuditAction.CREATE, "asset", part.getId(), null, part.auditValues());
        return part;
    }

    /** Changes custody, status and (optionally) condition, with an audit entry of the old and new values. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void move(Asset a, Long departmentId, String room, Long employeeId, AssetStatus status,
                     AssetCondition condition) {
        var before = a.auditValues();
        a.moveCustody(departmentId, room, employeeId);
        a.setStatus(status);
        if (condition != null) a.setCondition(condition);
        audit.recordChanges("asset", a.getId(), before, a.auditValues());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void setStatus(Asset a, AssetStatus status) {
        var before = a.auditValues();
        a.setStatus(status);
        audit.recordChanges("asset", a.getId(), before, a.auditValues());
    }

    /** UC-303 step 2: every item assigned to this person. */
    @Transactional(readOnly = true)
    public List<Asset> heldBy(Long employeeId) {
        return repo.findByEmployeeIdAndStatusOrderByDepartmentIdAscRoomAscIdAsc(employeeId, AssetStatus.ISSUED);
    }

    public record Brief(Long id, String propertyTagNo, String description, String unit) {
    }

    @Transactional(readOnly = true)
    public Map<Long, Brief> briefs(Collection<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        return repo.findAllById(ids).stream()
                .map(a -> new Brief(a.getId(), a.getPropertyTagNo(), a.getDescription(), a.getUnit()))
                .collect(Collectors.toMap(Brief::id, Function.identity()));
    }

    /** The split-off part gets its own copy of the photo, so replacing one never deletes the other's. */
    private void copyPhoto(Asset from, Asset to) {
        if (from.getPhotoPath() == null) return;
        try {
            String p = from.getPhotoPath();
            String ext = p.substring(p.lastIndexOf('.') + 1);
            to.setPhotoPath(storage.save("assets/" + to.getId() + "/photo-" + UUID.randomUUID() + "." + ext,
                    storage.load(p), null));
        } catch (IOException | RuntimeException e) {
            // The split still counts; the new item simply has no photo.
        }
    }
}
