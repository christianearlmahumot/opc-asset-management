package edu.citu.opc.fams.movement;

import edu.citu.opc.fams.asset.AssetCustodyService;
import edu.citu.opc.fams.common.ApiException;
import edu.citu.opc.fams.reference.Employee;
import edu.citu.opc.fams.reference.EmployeeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * UC-303 Process Resignation Turnover. Deliberately NOT transactional: every item goes through
 * MovementService.resignItem in its own transaction, so one failure is rolled back and listed as
 * pending while the others are saved (UC-303 5a). The HR notice itself is never stored.
 */
@Service
public class ResignationService {

    private static final Logger log = LoggerFactory.getLogger(ResignationService.class);

    private final MovementService movements;
    private final AssetCustodyService custody;
    private final EmployeeService employees;

    public ResignationService(MovementService movements, AssetCustodyService custody, EmployeeService employees) {
        this.movements = movements;
        this.custody = custody;
        this.employees = employees;
    }

    public record ItemFailure(Long assetId, String message) {
    }

    public record Outcome(int completed, List<ItemFailure> pending, int stillHeld, boolean markedResigned) {
    }

    public Outcome process(MovementRequests.Resignation r) {
        Employee person = employees.get(r.employeeId());
        int done = 0;
        List<ItemFailure> pending = new ArrayList<>();
        for (MovementRequests.ResignationItem item : r.items()) {
            try {
                movements.resignItem(person.getId(), item);
                done++;
            } catch (ApiException e) {
                pending.add(new ItemFailure(item.assetId(), e.getMessage()));
            } catch (RuntimeException e) {
                log.error("Resignation turnover failed for asset {}", item.assetId(), e);
                pending.add(new ItemFailure(item.assetId(), "Could not be saved. Try this item again."));
            }
        }
        int stillHeld = custody.heldBy(person.getId()).size();
        boolean resigned = false;
        if (r.markResigned() && stillHeld == 0) {
            employees.markResigned(person.getId());
            resigned = true;
        }
        return new Outcome(done, pending, stillHeld, resigned);
    }
}
