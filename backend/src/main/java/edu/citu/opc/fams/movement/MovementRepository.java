package edu.citu.opc.fams.movement;

import org.springframework.data.repository.Repository;

import java.util.List;

/** Append-only: movements are saved and read, never updated or deleted. */
public interface MovementRepository extends Repository<Movement, Long> {

    Movement save(Movement m);

    List<Movement> findByAssetIdOrderByRecordedAtDescIdDesc(Long assetId);
}
