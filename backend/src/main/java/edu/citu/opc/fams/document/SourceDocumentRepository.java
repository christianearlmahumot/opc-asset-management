package edu.citu.opc.fams.document;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface SourceDocumentRepository extends JpaRepository<SourceDocument, Long> {

    @Query("select d from SourceDocument d where d.type = :type and lower(d.docNo) = lower(:docNo)")
    List<SourceDocument> findSame(SourceDocument.Type type, String docNo);

    @Query(value = """
            select d from SourceDocument d
            where (:status is null or d.status = :status)
              and (:type is null or d.type = :type)
              and (:q is null or lower(d.docNo) like :q or lower(coalesce(d.poNo, '')) like :q
                   or lower(coalesce(d.prNo, '')) like :q or lower(coalesce(d.supplier, '')) like :q)
            order by d.createdAt desc, d.id desc
            """,
            countQuery = """
            select count(d) from SourceDocument d
            where (:status is null or d.status = :status)
              and (:type is null or d.type = :type)
              and (:q is null or lower(d.docNo) like :q or lower(coalesce(d.poNo, '')) like :q
                   or lower(coalesce(d.prNo, '')) like :q or lower(coalesce(d.supplier, '')) like :q)
            """)
    Page<SourceDocument> search(SourceDocument.Status status, SourceDocument.Type type, String q, Pageable pageable);

    /** UC-101 step 5: the documents of one purchase share a PO or PR number. */
    List<SourceDocument> findByPoNoIgnoreCase(String poNo);

    List<SourceDocument> findByPrNoIgnoreCase(String prNo);

    List<SourceDocument> findByStatusIn(Collection<SourceDocument.Status> statuses);
}
