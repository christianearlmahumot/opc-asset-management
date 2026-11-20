package edu.citu.opc.fams.reference;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DepartmentRepository extends JpaRepository<Department, Long> {

    List<Department> findAllByOrderByCodeAsc();

    Optional<Department> findByCodeIgnoreCase(String code);
}
