package edu.citu.opc.fams.issueorder;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Read-only questions other modules ask about issue orders: the asset register uses it to search by
 * IO number (UC-205) and to show an asset's IO number, as on the Property Inventory Slip.
 */
@Service
public class IssueOrderLookup {

    private final IssueOrderLineRepository lines;

    public IssueOrderLookup(IssueOrderLineRepository lines) {
        this.lines = lines;
    }

    public record Ref(Long id, String ioNo) {
    }

    @Transactional(readOnly = true)
    public List<Long> lineIdsForIoNo(String ioNo) {
        return lines.lineIdsForIoNo(ioNo);
    }

    @Transactional(readOnly = true)
    public Optional<Ref> orderOfLine(Long lineId) {
        if (lineId == null) return Optional.empty();
        return lines.orderOfLine(lineId).stream().findFirst()
                .map(r -> new Ref((Long) r[0], String.valueOf(r[1]).trim()));
    }
}
