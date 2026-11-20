package edu.citu.opc.fams.movement;

import edu.citu.opc.fams.access.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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
}
