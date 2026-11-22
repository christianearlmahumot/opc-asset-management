package edu.citu.opc.fams.document;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The documents of one purchase (UC-101 step 5): a sales invoice names the PO, the PO names the PR, so
 * following PO and PR numbers finds all three. Used by the review page, by department requests
 * (UC-201 step 4) and by the "items from the matching purchase" list (UC-202 step 2).
 */
@Service
public class PurchaseLookup {

    private final SourceDocumentRepository repo;

    public PurchaseLookup(SourceDocumentRepository repo) {
        this.repo = repo;
    }

    public record Linked(Long id, String type, String typeLabel, String docNo, String poNo, String prNo, String status) {
    }

    /** Every document reachable from these numbers through shared PO and PR numbers. */
    @Transactional(readOnly = true)
    public List<Linked> linked(String poNo, String prNo) {
        Map<Long, SourceDocument> found = new LinkedHashMap<>();
        Set<String> seen = new HashSet<>();
        Deque<String> todo = new ArrayDeque<>();
        if (present(poNo)) todo.add("PO:" + poNo.trim().toLowerCase(Locale.ROOT));
        if (present(prNo)) todo.add("PR:" + prNo.trim().toLowerCase(Locale.ROOT));
        int rounds = 0;
        while (!todo.isEmpty() && rounds++ < 20) {
            String key = todo.poll();
            if (!seen.add(key)) continue;
            String value = key.substring(3);
            List<SourceDocument> docs = key.startsWith("PO:") ? repo.findByPoNoIgnoreCase(value) : repo.findByPrNoIgnoreCase(value);
            for (SourceDocument d : docs) {
                if (found.putIfAbsent(d.getId(), d) != null) continue;
                if (present(d.getPoNo())) todo.add("PO:" + d.getPoNo().trim().toLowerCase(Locale.ROOT));
                if (present(d.getPrNo())) todo.add("PR:" + d.getPrNo().trim().toLowerCase(Locale.ROOT));
            }
        }
        List<SourceDocument> list = new ArrayList<>(found.values());
        list.sort(Comparator.comparing(SourceDocument::getType).thenComparing(SourceDocument::getCreatedAt));
        return list.stream().map(PurchaseLookup::view).toList();
    }

    /** UC-202 step 2: the source documents whose items belong to this PR's purchase. */
    @Transactional(readOnly = true)
    public List<Long> documentIdsForPurchase(String prNo) {
        if (!present(prNo)) return List.of();
        return linked(null, prNo).stream().map(Linked::id).toList();
    }

    static Linked view(SourceDocument d) {
        return new Linked(d.getId(), d.getType().name(), d.getType().label(), d.getDocNo(), d.getPoNo(), d.getPrNo(),
                d.getStatus().name());
    }

    private static boolean present(String s) {
        return s != null && !s.isBlank();
    }
}
