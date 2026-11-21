package edu.citu.opc.fams.issueorder;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface IssueOrderRepository extends JpaRepository<IssueOrder, Long>, JpaSpecificationExecutor<IssueOrder> {

    @Query("select max(o.ioNo) from IssueOrder o")
    String maxIoNo();

    Optional<IssueOrder> findByIoNo(String ioNo);

    /**
     * Transaction-scoped lock so two staff confirming at the same moment never get the same number.
     * Released automatically at commit or rollback.
     */
    @Query(value = "select 1 from (select pg_advisory_xact_lock(918401)) x", nativeQuery = true)
    Integer lockNumbering();
}
