package edu.citu.opc.fams.movement;

import edu.citu.opc.fams.access.CurrentUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes and reads movement rows. It knows nothing about assets, so both the asset module (encode,
 * condition edits) and the movement services of Step 3 can use it without depending on each other.
 */
@Service
public class MovementLedger {

    private final MovementRepository repo;

    public MovementLedger(MovementRepository repo) {
        this.repo = repo;
    }

    /**
     * MANDATORY: a movement is only ever written inside the caller's transaction, together with the
     * asset change it describes (REQ-306). Calling it outside a transaction is a programming error.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Movement record(Movement m) {
        CurrentUser.find().ifPresent(u -> m.recordedBy(u.id()));
        return repo.save(m);
    }

    @Transactional(readOnly = true)
    public List<Movement> history(Long assetId) {
        return repo.findByAssetIdOrderByRecordedAtDescIdDesc(assetId);
    }

    /**
     * UC-305: every return of an item to OPC storage, oldest first: turnovers (UC-302) and resignation
     * turnovers that sent the item back to the OPC (UC-303), not those handed to a new holder.
     */
    @Transactional(readOnly = true)
    public List<Movement> turnovers() {
        return repo.findByTypeInOrderByDateReturnedAscIdAsc(List.of(MovementType.TURNOVER, MovementType.RESIGNATION))
                .stream()
                .filter(m -> m.getType() == MovementType.TURNOVER || m.getToEmployeeId() == null)
                .toList();
    }

    /** UC-305: the disposal date recorded for each of these assets (UC-302 step 6), if any. */
    @Transactional(readOnly = true)
    public Map<Long, LocalDate> disposalDates(Collection<Long> assetIds) {
        Map<Long, LocalDate> m = new HashMap<>();
        List<Long> ids = List.copyOf(assetIds);
        for (int i = 0; i < ids.size(); i += 1000) {
            for (Movement d : repo.findByTypeAndAssetIdIn(MovementType.DISPOSAL, ids.subList(i, Math.min(i + 1000, ids.size())))) {
                if (d.getDisposalDate() != null) m.merge(d.getAssetId(), d.getDisposalDate(), (a, b) -> a.isAfter(b) ? a : b);
            }
        }
        return m;
    }

    @Transactional(readOnly = true)
    public Page<Movement> search(MovementType type, Long departmentId, int page, int size) {
        return repo.search(type, departmentId, PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100)));
    }
}
