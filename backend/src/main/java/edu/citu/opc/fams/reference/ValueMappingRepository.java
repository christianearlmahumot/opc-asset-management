package edu.citu.opc.fams.reference;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ValueMappingRepository extends JpaRepository<ValueMapping, Long> {

    @Query("select m from ValueMapping m where m.kind = :kind and lower(m.sourceValue) = lower(:source)")
    Optional<ValueMapping> find(ValueMapping.Kind kind, String source);

    @Query("select distinct m.mappedValue from ValueMapping m where m.kind = :kind order by m.mappedValue")
    List<String> mappedValues(ValueMapping.Kind kind);
}
