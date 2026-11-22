package edu.citu.opc.fams.asset;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
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

    /** SELECT … FOR UPDATE: two people cannot move the same item at the same moment. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Asset a where a.id = :id")
    Optional<Asset> findForUpdate(Long id);

    List<Asset> findByEmployeeIdAndStatusOrderByDepartmentIdAscRoomAscIdAsc(Long employeeId, AssetStatus status);

    List<Asset> findByParentAssetId(Long parentAssetId);

    /** UC-102: the items encoded from one purchasing document. */
    List<Asset> findBySourceDocumentIdOrderByIdAsc(Long sourceDocumentId);

    List<Asset> findByIssueOrderLineIdIn(java.util.Collection<Long> lineIds);

    /** UC-304: the items a department currently holds. */
    List<Asset> findByDepartmentIdAndStatusIn(Long departmentId, java.util.Collection<AssetStatus> statuses);

    /** UC-304: items held by the OPC itself (no department): in storage or turned over. */
    List<Asset> findByDepartmentIdIsNullAndStatusIn(java.util.Collection<AssetStatus> statuses);
}
