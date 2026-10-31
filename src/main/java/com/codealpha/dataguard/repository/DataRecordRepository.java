package com.codealpha.dataguard.repository;

import com.codealpha.dataguard.entity.DataRecord;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DataRecordRepository extends JpaRepository<DataRecord, Long> {
    Optional<DataRecord> findByExternalIdIgnoreCase(String externalId);
    Optional<DataRecord> findByFingerprint(String fingerprint);

    @Query("select r from DataRecord r where " +
            "(:query is null or lower(r.externalId) like lower(concat('%', :query, '%')) " +
            "or lower(r.name) like lower(concat('%', :query, '%')) " +
            "or lower(coalesce(r.email, '')) like lower(concat('%', :query, '%'))) and " +
            "(:category is null or lower(r.category) = lower(:category)) " +
            "order by r.createdAt desc")
    List<DataRecord> search(@Param("query") String query, @Param("category") String category);

    @Query("select r.category, count(r) from DataRecord r group by r.category order by count(r) desc")
    List<Object[]> countByCategory();
}
