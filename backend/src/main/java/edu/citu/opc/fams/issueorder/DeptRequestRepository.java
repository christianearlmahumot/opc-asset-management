package edu.citu.opc.fams.issueorder;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DeptRequestRepository extends JpaRepository<DeptRequest, Long> {

    List<DeptRequest> findByStatusOrderByCreatedAtDesc(DeptRequest.Status status);

    List<DeptRequest> findTop100ByOrderByCreatedAtDesc();
}
