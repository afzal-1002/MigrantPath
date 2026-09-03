package com.foreignerwarsaw.usercase.core;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserCaseRepository extends JpaRepository<UserCase, UUID> {

  @Query(
      """
      SELECT c FROM UserCase c
      JOIN FETCH c.procedure p JOIN FETCH p.category
      LEFT JOIN FETCH c.currentRevision
      WHERE c.id = :id
      """)
  Optional<UserCase> findByIdFetchingProcedureAndRevision(@Param("id") UUID id);

  Optional<UserCase> findByRecommendation_Id(UUID recommendationId);

  @Query(
      """
      SELECT c FROM UserCase c
      JOIN FETCH c.procedure p JOIN FETCH p.category
      LEFT JOIN FETCH c.currentRevision
      WHERE c.user.id = :userId
      ORDER BY c.updatedAt DESC
      """)
  List<UserCase> findByUser_IdOrderByUpdatedAtDesc(@Param("userId") UUID userId);
}
