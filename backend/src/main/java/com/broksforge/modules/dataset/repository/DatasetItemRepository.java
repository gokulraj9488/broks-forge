package com.broksforge.modules.dataset.repository;

import com.broksforge.modules.dataset.domain.DatasetItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DatasetItemRepository extends JpaRepository<DatasetItem, UUID> {

    Page<DatasetItem> findByDatasetVersionIdOrderBySequenceAsc(UUID datasetVersionId, Pageable pageable);

    List<DatasetItem> findByDatasetVersionIdOrderBySequenceAsc(UUID datasetVersionId);

    long countByDatasetVersionId(UUID datasetVersionId);

    @Query("""
            SELECT new com.broksforge.modules.dataset.repository.DatasetVersionStats(
                COUNT(i),
                SUM(CASE WHEN i.expectedOutput IS NOT NULL AND i.expectedOutput <> '' THEN 1 ELSE 0 END),
                AVG(LENGTH(i.input)),
                AVG(LENGTH(i.expectedOutput)))
            FROM DatasetItem i
            WHERE i.datasetVersionId = :versionId
            """)
    DatasetVersionStats computeStats(@Param("versionId") UUID versionId);
}
