package edu.citu.opc.fams.reference;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Long>, JpaSpecificationExecutor<Employee> {

    Optional<Employee> findByEmployeeNoIgnoreCase(String employeeNo);

    @Query("select e.id from Employee e where lower(e.fullName) like lower(concat('%', :q, '%'))")
    List<Long> findIdsByNameContaining(String q);
}
