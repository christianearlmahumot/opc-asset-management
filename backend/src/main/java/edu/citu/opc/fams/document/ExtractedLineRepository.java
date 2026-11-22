package edu.citu.opc.fams.document;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ExtractedLineRepository extends JpaRepository<ExtractedLine, Long> {

    List<ExtractedLine> findByDocumentIdOrderByLineNoAsc(Long documentId);

    @Modifying
    @Query("delete from ExtractedLine l where l.documentId = :documentId and l.confirmed = false")
    void deleteUnconfirmed(Long documentId);
}
