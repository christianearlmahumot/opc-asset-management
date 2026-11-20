package edu.citu.opc.fams.reference;

import edu.citu.opc.fams.audit.AuditAction;
import edu.citu.opc.fams.audit.AuditService;
import edu.citu.opc.fams.common.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class CategoryService {

    private final CategoryRepository repo;
    private final AuditService audit;

    public CategoryService(CategoryRepository repo, AuditService audit) {
        this.repo = repo;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<Category> list() {
        return repo.findAllByOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public boolean exists(Long id) {
        return id != null && repo.existsById(id);
    }

    @Transactional(readOnly = true)
    public Map<Long, String> names() {
        return repo.findAll().stream().collect(Collectors.toMap(Category::getId, Category::getName));
    }

    @Transactional
    public Category create(String name) {
        String n = name.trim();
        if (repo.findByNameIgnoreCase(n).isPresent()) {
            throw ApiException.field("name", "The category " + n + " already exists.");
        }
        Category c = repo.save(new Category(n));
        audit.record(AuditAction.CREATE, "category", c.getId(), null, Map.of("name", n));
        return c;
    }

    @Transactional
    public Category rename(Long id, String name) {
        Category c = repo.findById(id).orElseThrow(() -> ApiException.notFound("Category"));
        String n = name.trim();
        repo.findByNameIgnoreCase(n).filter(o -> !o.getId().equals(id)).ifPresent(o -> {
            throw ApiException.field("name", "The category " + n + " already exists.");
        });
        String old = c.getName();
        c.setName(n);
        audit.recordChanges("category", id, Map.of("name", old), Map.of("name", n));
        return c;
    }
}
