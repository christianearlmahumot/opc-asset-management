package edu.citu.opc.fams.asset;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * SDD 2.1: finds assets with the same serial number, or the same category, item, brand and model.
 * (An identical property tag is not a warning but a hard error, checked by AssetService.)
 */
@Service
public class DuplicateCheckService {

    private final AssetRepository repo;

    public DuplicateCheckService(AssetRepository repo) {
        this.repo = repo;
    }

    public record Match(Long id, String propertyTagNo, String description, String serialNo, String reason) {
    }

    @Transactional(readOnly = true)
    public List<Match> find(String serialNo, Long categoryId, String description, String brand, String model,
                            Long excludeId) {
        Map<Long, Match> found = new LinkedHashMap<>();
        if (serialNo != null && !serialNo.isBlank()) {
            for (Asset a : repo.findBySerial(serialNo.trim())) {
                add(found, a, excludeId, "serial no. " + a.getSerialNo());
            }
        }
        if (categoryId != null && description != null && !description.isBlank()) {
            for (Asset a : repo.findSameItem(categoryId, description.trim(), nz(brand), nz(model))) {
                add(found, a, excludeId, "an item with the same category, description, brand and model");
            }
        }
        return new ArrayList<>(found.values());
    }

    private static void add(Map<Long, Match> found, Asset a, Long excludeId, String reason) {
        if (Objects.equals(a.getId(), excludeId) || found.containsKey(a.getId())) return;
        found.put(a.getId(), new Match(a.getId(), a.getPropertyTagNo(), a.getDescription(), a.getSerialNo(), reason));
    }

    /** Empty string instead of null, so the coalesce() in the query has a known parameter type. */
    private static String nz(String s) {
        return s == null ? "" : s.trim();
    }
}
