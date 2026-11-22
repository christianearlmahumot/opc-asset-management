package edu.citu.opc.fams.issueorder;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface IssueOrderLineRepository extends JpaRepository<IssueOrderLine, Long> {

    List<IssueOrderLine> findByIssueOrderIdOrderByIdAsc(Long issueOrderId);

    /** UC-305 masterlist export: the lines of many orders at once. */
    List<IssueOrderLine> findByIssueOrderIdInOrderByIdAsc(Collection<Long> issueOrderIds);

    @Query("select l.issueOrderId, count(l) from IssueOrderLine l where l.issueOrderId in :ids group by l.issueOrderId")
    List<Object[]> countByOrders(Collection<Long> ids);

    @Query("select l.id from IssueOrderLine l, IssueOrder o where o.id = l.issueOrderId and o.ioNo = :ioNo")
    List<Long> lineIdsForIoNo(String ioNo);

    @Query("select o.id, o.ioNo from IssueOrderLine l, IssueOrder o where o.id = l.issueOrderId and l.id = :lineId")
    List<Object[]> orderOfLine(Long lineId);
}
