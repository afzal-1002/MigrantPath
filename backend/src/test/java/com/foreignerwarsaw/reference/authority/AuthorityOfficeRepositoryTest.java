package com.foreignerwarsaw.reference.authority;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.foreignerwarsaw.TestcontainersConfiguration;
import com.foreignerwarsaw.reference.geography.City;
import com.foreignerwarsaw.reference.geography.CityRepository;
import com.foreignerwarsaw.reference.geography.District;
import com.foreignerwarsaw.reference.geography.DistrictRepository;
import com.foreignerwarsaw.reference.geography.Jurisdiction;
import com.foreignerwarsaw.reference.geography.JurisdictionRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Real Testcontainers PostgreSQL, real V16/V17-applied schema and seed data. Includes the
 * regression test for the two {@code LazyInitializationException} bugs found during Phase 3's own
 * manual verification (both repositories' {@code search} queries originally either omitted a fetch
 * join entirely, or navigated a nullable to-one association inline in the WHERE clause, which
 * compiles to a silently-filtering INNER JOIN) - see {@link AuthorityRepository#search} and {@link
 * OfficeRepository#search} Javadoc for the full explanation.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class AuthorityOfficeRepositoryTest {

  @Autowired private AuthorityRepository authorityRepository;
  @Autowired private OfficeRepository officeRepository;
  @Autowired private OfficeServiceRepository officeServiceRepository;
  @Autowired private JurisdictionRepository jurisdictionRepository;
  @Autowired private CityRepository cityRepository;
  @Autowired private DistrictRepository districtRepository;
  @Autowired private TestEntityManager testEntityManager;

  @Test
  void search_returnsAllThreeSeedAuthoritiesAcrossAllThreeJurisdictionLevels() {
    List<Authority> authorities = authorityRepository.search(null, null, null);

    assertThat(authorities)
        .extracting(Authority::getCode)
        .containsExactlyInAnyOrder("UDSC", "MAZOWIECKIE_VOIVODESHIP_OFFICE", "WARSAW_CITY_HALL");
  }

  @Test
  void search_regionalJurisdictionWithNoCity_isNotSilentlyDroppedByAnUnfilteredCityParam() {
    // The bug this specifically regression-tests: MAZOWIECKIE_VOIVODESHIP_OFFICE's
    // jurisdiction (PL_MAZOWIECKIE, REGIONAL) has city_id = NULL by the V14 CHECK
    // constraint. An implicit `j.city.code` path join compiles to an INNER join and
    // would drop this row even with city=null (i.e. "no filter").
    List<Authority> authorities = authorityRepository.search(null, null, null);

    assertThat(authorities)
        .extracting(Authority::getCode)
        .contains("MAZOWIECKIE_VOIVODESHIP_OFFICE");
  }

  @Test
  void search_filteredByCity_onlyReturnsAuthoritiesWhoseJurisdictionHasThatCity() {
    List<Authority> warsawAuthorities = authorityRepository.search(null, "WARSAW", null);

    assertThat(warsawAuthorities)
        .extracting(Authority::getCode)
        .containsExactly("WARSAW_CITY_HALL");
  }

  @Test
  void search_unknownFilterValues_returnEmptyNotAnError() {
    assertThat(authorityRepository.search(null, "NOPE", null)).isEmpty();
    assertThat(authorityRepository.search("NOPE", null, null)).isEmpty();
    assertThat(authorityRepository.search(null, null, "NOPE")).isEmpty();
  }

  @Test
  void search_resultsSurviveThePersistenceContextClosing() {
    // Regression test for the original LazyInitializationException: JOIN FETCH must
    // make `jurisdiction` already-initialized, so reading it after the persistence
    // context is gone (exactly what the controller does, one layer above the
    // @Transactional service method) doesn't throw.
    List<Authority> authorities = authorityRepository.search(null, null, null);
    testEntityManager.getEntityManager().clear();

    assertThatCode(() -> authorities.forEach(a -> a.getJurisdiction().getCode()))
        .doesNotThrowAnyException();
  }

  @Test
  void authorityHierarchy_childResolvesItsParentByCode() {
    Jurisdiction poland = jurisdictionRepository.findByCode("PL").orElseThrow();
    Authority parent =
        persistAuthority("TEST_PARENT_AUTHORITY", "Test Parent", "NATIONAL_AGENCY", poland, null);
    Authority child =
        persistAuthority("TEST_CHILD_AUTHORITY", "Test Child", "NATIONAL_AGENCY", poland, parent);
    testEntityManager.getEntityManager().flush();
    testEntityManager.getEntityManager().clear();

    Authority reloadedChild =
        authorityRepository.search(null, null, null).stream()
            .filter(a -> a.getCode().equals("TEST_CHILD_AUTHORITY"))
            .findFirst()
            .orElseThrow();

    assertThat(reloadedChild.getParentAuthority().getCode()).isEqualTo("TEST_PARENT_AUTHORITY");
  }

  @Test
  void search_seedOffice_resolvesAuthorityCityAndNullDistrict() {
    List<Office> offices = officeRepository.search(null, null, null, null);

    assertThat(offices).hasSize(1);
    Office office = offices.get(0);
    assertThat(office.getCode()).isEqualTo("MAZOWIECKIE_WSC_MARSZALKOWSKA");
    assertThat(office.getAuthority().getCode()).isEqualTo("MAZOWIECKIE_VOIVODESHIP_OFFICE");
    assertThat(office.getCity().getCode()).isEqualTo("WARSAW");
    assertThat(office.getDistrict()).as("seed office has no district assigned").isNull();
  }

  @Test
  void search_officeWithADistrict_resolvesItToo() {
    // The seed office has no district (offices aren't yet district-routed - brief
    // §19/§48) - this proves the LEFT JOIN FETCH path for a *present* district also
    // works, not just the null case the seed data happens to exercise.
    Authority authority =
        authorityRepository.search(null, null, null).stream()
            .filter(a -> a.getCode().equals("UDSC"))
            .findFirst()
            .orElseThrow();
    City warsaw = cityRepository.findByCodeIgnoreCase("WARSAW").orElseThrow();
    District srodmiescie =
        districtRepository
            .findByCity_CodeIgnoreCaseAndActiveTrueOrderByCanonicalNameAsc("WARSAW")
            .stream()
            .filter(d -> d.getCode().equals("SRODMIESCIE"))
            .findFirst()
            .orElseThrow();
    Office officeWithDistrict =
        persistOffice("TEST_OFFICE_WITH_DISTRICT", authority, warsaw, srodmiescie);
    testEntityManager.getEntityManager().flush();
    testEntityManager.getEntityManager().clear();

    Office reloaded =
        officeRepository.search(null, null, null, null).stream()
            .filter(o -> o.getCode().equals("TEST_OFFICE_WITH_DISTRICT"))
            .findFirst()
            .orElseThrow();

    assertThat(reloaded.getDistrict()).isNotNull();
    assertThat(reloaded.getDistrict().getCode()).isEqualTo("SRODMIESCIE");
  }

  @Test
  void search_filteredByService_onlyReturnsOfficesOfferingThatService() {
    assertThat(officeRepository.search(null, null, null, "IMMIGRATION_INFORMATION"))
        .extracting(Office::getCode)
        .contains("MAZOWIECKIE_WSC_MARSZALKOWSKA");
    assertThat(officeRepository.search(null, null, null, "PESEL")).isEmpty();
  }

  @Test
  void search_officeResultsSurviveThePersistenceContextClosing() {
    List<Office> offices = officeRepository.search(null, null, null, null);
    testEntityManager.getEntityManager().clear();

    assertThatCode(
            () ->
                offices.forEach(
                    o -> {
                      o.getAuthority().getCode();
                      o.getCity().getCode();
                    }))
        .doesNotThrowAnyException();
  }

  @Test
  void officeServiceRepository_findsOnlyActiveServicesForTheGivenOffice() {
    List<OfficeService> services =
        officeServiceRepository.findByOffice_CodeAndActiveTrue("MAZOWIECKIE_WSC_MARSZALKOWSKA");

    assertThat(services)
        .extracting(os -> os.getServiceType().getCode())
        .containsExactly("IMMIGRATION_INFORMATION");
  }

  private Authority persistAuthority(
      String code, String name, String type, Jurisdiction jurisdiction, Authority parent) {
    Authority authority = new Authority();
    ReflectionTestUtils.setField(authority, "code", code);
    ReflectionTestUtils.setField(authority, "canonicalName", name);
    ReflectionTestUtils.setField(authority, "authorityType", type);
    ReflectionTestUtils.setField(authority, "jurisdiction", jurisdiction);
    ReflectionTestUtils.setField(authority, "parentAuthority", parent);
    ReflectionTestUtils.setField(authority, "validFrom", LocalDate.of(2020, 1, 1));
    return testEntityManager.persist(authority);
  }

  private Office persistOffice(String code, Authority authority, City city, District district) {
    Office office = new Office();
    ReflectionTestUtils.setField(office, "code", code);
    ReflectionTestUtils.setField(office, "authority", authority);
    ReflectionTestUtils.setField(office, "canonicalName", "Test Office " + code);
    ReflectionTestUtils.setField(office, "city", city);
    ReflectionTestUtils.setField(office, "district", district);
    ReflectionTestUtils.setField(office, "validFrom", LocalDate.of(2020, 1, 1));
    return testEntityManager.persist(office);
  }
}
