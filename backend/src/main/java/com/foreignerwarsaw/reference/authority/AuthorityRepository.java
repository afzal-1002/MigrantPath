package com.foreignerwarsaw.reference.authority;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuthorityRepository extends JpaRepository<Authority, UUID> {

  /**
   * Only the three filters brief §30 actually asks for - the {@code (:x IS NULL OR ...)} pattern is
   * deliberately simple (no Specification API) since three optional filters don't justify that
   * machinery yet.
   *
   * <p>{@code JOIN FETCH a.jurisdiction} is required, not optional: {@link
   * com.foreignerwarsaw.reference.authority.dto.AuthorityResponse#from} reads {@code
   * jurisdiction.code} after the transactional service method has returned, so the association must
   * already be initialized - otherwise it's a {@code LazyInitializationException} (no open session
   * in the controller).
   *
   * <p>{@code j.city} must be an explicit {@code LEFT JOIN}, not a {@code j.city.code} path
   * expression inline in the WHERE clause: a REGIONAL jurisdiction has {@code city_id IS NULL} by
   * the V14 CHECK constraint (only MUNICIPAL jurisdictions have a city), and an implicit
   * path-navigation join through a nullable to-one association compiles to an INNER join - which
   * would silently drop every REGIONAL-jurisdiction authority (e.g. the Mazowieckie Voivodeship
   * Office) even when {@code :cityCode} is null and the filter is meant to be a no-op.
   */
  @Query(
      """
      SELECT a FROM Authority a
      JOIN FETCH a.jurisdiction j
      LEFT JOIN j.city c
      WHERE a.active = TRUE
        AND (:jurisdictionCode IS NULL OR j.code = :jurisdictionCode)
        AND (:cityCode IS NULL OR c.code = :cityCode)
        AND (:authorityType IS NULL OR a.authorityType = :authorityType)
      ORDER BY a.canonicalName
      """)
  List<Authority> search(
      @Param("jurisdictionCode") String jurisdictionCode,
      @Param("cityCode") String cityCode,
      @Param("authorityType") String authorityType);
}
