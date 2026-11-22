package edu.citu.opc.fams.importer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ImportBatchRepository extends JpaRepository<ImportBatch, Long> {

    List<ImportBatch> findTop20ByOrderByImportedAtDescIdDesc();

    boolean existsByStatus(ImportBatch.Status status);

    /** At start-up: an import cut off by a restart was rolled back by the database, so say so. */
    @Modifying
    @Query("""
            update ImportBatch b set b.status = edu.citu.opc.fams.importer.ImportBatch.Status.FAILED,
                b.error = 'The server restarted while importing. Nothing was saved; run the import again.',
                b.finishedAt = CURRENT_TIMESTAMP
            where b.status = edu.citu.opc.fams.importer.ImportBatch.Status.RUNNING
            """)
    int failInterrupted();
}
