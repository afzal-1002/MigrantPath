package com.foreignerwarsaw.reference.authority;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OfficeRepository extends JpaRepository<Office, UUID> {

  /**
   * Same "only the filters we need" reasoning as {@link AuthorityRepository#search} - brief §31's
   * example call is exactly city+district+service.
   *
   * <p>{@code o.authority} and {@code o.city} are {@code JOIN FETCH}'d (not just joined for
   * filtering) because {@link com.foreignerwarsaw.reference.authority.dto.OfficeResponse#of} reads
   * {@code authority.code} and {@code city.code} after the transactional service method has
   * returned - without the fetch, that's a {@code LazyInitializationException} (no open session in
   * the controller). {@code district} is {@code LEFT JOIN FETCH} for the same reason, since it's
   * optional on the entity.
   */
  @Query(
      """
      SELECT DISTINCT o FROM Office o
      JOIN FETCH o.authority
      JOIN FETCH o.city
      LEFT JOIN FETCH o.district d
      LEFT JOIN OfficeService os ON os.office = o AND os.active = TRUE
      LEFT JOIN os.serviceType st
      WHERE o.active = TRUE
        AND (:cityCode IS NULL OR o.city.code = :cityCode)
        AND (:districtCode IS NULL OR d.code = :districtCode)
        AND (:authorityCode IS NULL OR o.authority.code = :authorityCode)
        AND (:serviceCode IS NULL OR st.code = :serviceCode)
      ORDER BY o.canonicalName
      """)
  List<Office> search(
      @Param("cityCode") String cityCode,
      @Param("districtCode") String districtCode,
      @Param("authorityCode") String authorityCode,
      @Param("serviceCode") String serviceCode);
}
