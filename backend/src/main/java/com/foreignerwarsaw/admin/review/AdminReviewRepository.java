package com.foreignerwarsaw.admin.review;

import com.foreignerwarsaw.common.audit.AuditEntityType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdminReviewRepository extends JpaRepository<AdminReview, UUID> {

  Optional<AdminReview> findByEntityTypeAndEntityVersionIdAndStatus(
      AuditEntityType entityType, UUID entityVersionId, AdminReviewStatus status);

  /**
   * Fetch-joins {@code submittedBy}/{@code reviewer} - both are read by {@code
   * AdminReviewResponse#from} after the transactional service method that loaded them has already
   * returned (same LazyInitializationException risk documented throughout this codebase's other
   * repositories).
   */
  @Query(
      "SELECT r FROM AdminReview r JOIN FETCH r.submittedBy LEFT JOIN FETCH r.reviewer"
          + " WHERE r.entityType = :entityType AND r.entityVersionId = :entityVersionId"
          + " ORDER BY r.createdAt DESC")
  List<AdminReview> findByEntityTypeAndEntityVersionIdOrderByCreatedAtDesc(
      @Param("entityType") AuditEntityType entityType,
      @Param("entityVersionId") UUID entityVersionId);

  @Query(
      "SELECT r FROM AdminReview r JOIN FETCH r.submittedBy LEFT JOIN FETCH r.reviewer"
          + " WHERE r.status = :status ORDER BY r.createdAt ASC")
  List<AdminReview> findByStatusOrderByCreatedAtAsc(@Param("status") AdminReviewStatus status);
}
