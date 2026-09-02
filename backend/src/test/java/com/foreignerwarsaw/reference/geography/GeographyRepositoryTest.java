package com.foreignerwarsaw.reference.geography;

import static org.assertj.core.api.Assertions.assertThat;

import com.foreignerwarsaw.TestcontainersConfiguration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

/**
 * Real Testcontainers PostgreSQL, real V12/V13-applied schema and seed data - the Region -> City ->
 * District chain (docs/database/DATABASE.md §2).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class GeographyRepositoryTest {

  @Autowired private RegionRepository regionRepository;
  @Autowired private CityRepository cityRepository;
  @Autowired private DistrictRepository districtRepository;

  @Test
  void allSixteenVoivodeshipsAreSeededAndBelongToPoland() {
    List<Region> regions =
        regionRepository.findByCountry_CodeIgnoreCaseAndActiveTrueOrderByCanonicalNameAsc("PL");

    assertThat(regions).hasSize(16);
    assertThat(regions).allMatch(r -> r.getCountry().getCode().equals("PL"));
    assertThat(regions).extracting(Region::getCode).contains("MAZOWIECKIE");
  }

  @Test
  void warsawIsTheOnlyActiveCityAndBelongsToMazowieckie() {
    List<City> cities =
        cityRepository.findByRegion_CodeIgnoreCaseAndActiveTrueOrderByCanonicalNameAsc(
            "MAZOWIECKIE");

    assertThat(cities).hasSize(1);
    assertThat(cities.get(0).getCode()).isEqualTo("WARSAW");
    assertThat(cities.get(0).getRegion().getCode()).isEqualTo("MAZOWIECKIE");
  }

  @Test
  void cityActiveDefaultsFalse_soANewlyPersistedCityIsNotReturnedUntilExplicitlyActivated() {
    // City.active defaults to false (City.java) - a genuinely different default from
    // every other reference entity here, and this is the behavioural proof of it:
    // enabling a future city is an explicit flip, never an accidental side effect of
    // just inserting the row.
    Region mazowieckie =
        regionRepository
            .findByCountry_CodeIgnoreCaseAndActiveTrueOrderByCanonicalNameAsc("PL")
            .stream()
            .filter(r -> r.getCode().equals("MAZOWIECKIE"))
            .findFirst()
            .orElseThrow();

    City notYetActive = new City();
    org.springframework.test.util.ReflectionTestUtils.setField(notYetActive, "code", "KRAKOW");
    org.springframework.test.util.ReflectionTestUtils.setField(
        notYetActive, "canonicalName", "Kraków");
    org.springframework.test.util.ReflectionTestUtils.setField(
        notYetActive, "country", mazowieckie.getCountry());
    org.springframework.test.util.ReflectionTestUtils.setField(notYetActive, "region", mazowieckie);
    // Arbitrary fixture date - only its presence (NOT NULL) matters for this test.
    org.springframework.test.util.ReflectionTestUtils.setField(
        notYetActive, "validFrom", java.time.LocalDate.of(2030, 1, 1));
    cityRepository.saveAndFlush(notYetActive);

    List<City> activeCitiesInMazowieckie =
        cityRepository.findByRegion_CodeIgnoreCaseAndActiveTrueOrderByCanonicalNameAsc(
            "MAZOWIECKIE");

    assertThat(activeCitiesInMazowieckie).extracting(City::getCode).doesNotContain("KRAKOW");
  }

  @Test
  void warsawHasExactlyEighteenActiveDistrictsWithPolishDiacriticsPreserved() {
    List<District> districts =
        districtRepository.findByCity_CodeIgnoreCaseAndActiveTrueOrderByCanonicalNameAsc("WARSAW");

    assertThat(districts).hasSize(18);
    assertThat(districts).allMatch(d -> d.getCity().getCode().equals("WARSAW"));
    assertThat(districts)
        .extracting(District::getCanonicalName)
        .contains("Białołęka", "Śródmieście", "Żoliborz", "Praga-Południe", "Praga-Północ");
  }

  @Test
  void unknownRegionOrCity_returnsEmptyListNotAnError() {
    assertThat(
            cityRepository.findByRegion_CodeIgnoreCaseAndActiveTrueOrderByCanonicalNameAsc("NOPE"))
        .isEmpty();
    assertThat(
            districtRepository.findByCity_CodeIgnoreCaseAndActiveTrueOrderByCanonicalNameAsc(
                "NOPE"))
        .isEmpty();
  }
}
