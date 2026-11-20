package edu.citu.opc.fams.asset;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface AssetRepository extends JpaRepository<Asset, Long>, JpaSpecificationExecutor<Asset> {

    Optional<Asset> findByPropertyTagNoIgnoreCase(String tag);

    @Query("select a from Asset a where lower(a.serialNo) = lower(:serial)")
    List<Asset> findBySerial(String serial);

    @Query("""
            select a from Asset a
            where a.categoryId = :categoryId
              and lower(a.description) = lower(:description)
              and lower(coalesce(a.brand, '')) = lower(coalesce(:brand, ''))
              and lower(coalesce(a.modelNo, '')) = lower(coalesce(:model, ''))
            """)
    List<Asset> findSameItem(Long categoryId, String description, String brand, String model);

    @Query("select a.propertyTagNo from Asset a where upper(a.propertyTagNo) like upper(concat(:prefix, '%'))")
    List<String> findTagsStartingWith(String prefix);
}
