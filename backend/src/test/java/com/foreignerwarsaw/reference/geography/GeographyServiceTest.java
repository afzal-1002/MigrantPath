package com.foreignerwarsaw.reference.geography;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class GeographyServiceTest {

  @Mock private RegionRepository regionRepository;
  @Mock private CityRepository cityRepository;
  @Mock private DistrictRepository districtRepository;

  private GeographyService service;

  @BeforeEach
  void setUp() {
    service = new GeographyService(regionRepository, cityRepository, districtRepository);
  }

  @Test
  void regionsForCountry_unknownCountry_returnsEmptyListNotAnException() {
    when(regionRepository.findByCountry_CodeIgnoreCaseAndActiveTrueOrderByCanonicalNameAsc("ZZ"))
        .thenReturn(List.of());

    assertThat(service.regionsForCountry("ZZ")).isEmpty();
  }

  @Test
  void regionsForCountry_delegatesToTheActiveOnlyOrderedQuery() {
    Region mazowieckie = regionWithCode("MAZOWIECKIE");
    when(regionRepository.findByCountry_CodeIgnoreCaseAndActiveTrueOrderByCanonicalNameAsc("PL"))
        .thenReturn(List.of(mazowieckie));

    assertThat(service.regionsForCountry("PL")).containsExactly(mazowieckie);
    verify(regionRepository).findByCountry_CodeIgnoreCaseAndActiveTrueOrderByCanonicalNameAsc("PL");
  }

  @Test
  void citiesForRegion_delegatesToTheActiveOnlyOrderedQuery() {
    City warsaw = cityWithCode("WARSAW");
    when(cityRepository.findByRegion_CodeIgnoreCaseAndActiveTrueOrderByCanonicalNameAsc(
            "MAZOWIECKIE"))
        .thenReturn(List.of(warsaw));

    assertThat(service.citiesForRegion("MAZOWIECKIE")).containsExactly(warsaw);
  }

  @Test
  void districtsForCity_delegatesToTheActiveOnlyOrderedQuery() {
    District srodmiescie = districtWithCode("SRODMIESCIE");
    when(districtRepository.findByCity_CodeIgnoreCaseAndActiveTrueOrderByCanonicalNameAsc("WARSAW"))
        .thenReturn(List.of(srodmiescie));

    assertThat(service.districtsForCity("WARSAW")).containsExactly(srodmiescie);
  }

  private Region regionWithCode(String code) {
    Region region = new Region();
    ReflectionTestUtils.setField(region, "code", code);
    return region;
  }

  private City cityWithCode(String code) {
    City city = new City();
    ReflectionTestUtils.setField(city, "code", code);
    return city;
  }

  private District districtWithCode(String code) {
    District district = new District();
    ReflectionTestUtils.setField(district, "code", code);
    return district;
  }
}
