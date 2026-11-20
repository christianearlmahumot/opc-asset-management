package edu.citu.opc.fams.movement;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

import java.util.List;

/** Append-only: movements are saved and read, never updated or deleted. */
public interface MovementRepository extends Repository<Movement, Long> {

    Movement save(Movement m);

    List<Movement> findByAssetIdOrderByRecordedAtDescIdDesc(Long assetId);

    /** Recent movements for the Movements page, optionally by type and by department (from or to). */
    @Query(value = """
            select m from Movement m
            where (:type is null or m.type = :type)
              and (:dept is null or m.fromDepartmentId = :dept or m.toDepartmentId = :dept)
            order by m.recordedAt desc, m.id desc
            """,
            countQuery = """
            select count(m) from Movement m
            where (:type is null or m.type = :type)
              and (:dept is null or m.fromDepartmentId = :dept or m.toDepartmentId = :dept)
            """)
    Page<Movement> search(MovementType type, Long dept, Pageable pageable);
}
