package edu.citu.opc.fams.reference;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** The reference module's public face. Other modules use this, never the repository. */
@Service
public class DepartmentService {

    private final DepartmentRepository repo;

    public DepartmentService(DepartmentRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public List<Department> list() {
        return repo.findAllByOrderByCodeAsc();
    }

    @Transactional(readOnly = true)
    public boolean exists(Long id) {
        return id != null && repo.existsById(id);
    }
}
