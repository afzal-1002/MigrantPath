package com.foreignerwarsaw.reference.country;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CountryGroupMembershipRepository
    extends JpaRepository<CountryGroupMembership, UUID> {

  /**
   * The reusable "active membership as of date X" query (brief §16/§41) - inclusive {@code
   * validTo}, per ADR-006's temporal convention for reference data.
   */
  @Query(
      """
      SELECT m FROM CountryGroupMembership m
      WHERE m.country.id = :countryId
        AND m.validFrom <= :evaluationDate
        AND (m.validTo IS NULL OR m.validTo >= :evaluationDate)
      """)
  List<CountryGroupMembership> findActiveMembershipsForCountry(
      @Param("countryId") UUID countryId, @Param("evaluationDate") LocalDate evaluationDate);

  @Query(
      """
      SELECT m FROM CountryGroupMembership m
      WHERE m.countryGroup.id = :groupId
        AND m.validFrom <= :evaluationDate
        AND (m.validTo IS NULL OR m.validTo >= :evaluationDate)
      """)
  List<CountryGroupMembership> findActiveMembershipsForGroup(
      @Param("groupId") UUID groupId, @Param("evaluationDate") LocalDate evaluationDate);
}
