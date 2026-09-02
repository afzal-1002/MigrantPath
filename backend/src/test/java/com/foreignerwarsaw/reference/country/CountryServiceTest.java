package com.foreignerwarsaw.reference.country;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.foreignerwarsaw.common.web.ApiException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CountryServiceTest {

  @Mock private CountryRepository countryRepository;

  private CountryService service;

  @BeforeEach
  void setUp() {
    service = new CountryService(countryRepository);
  }

  @Test
  void listActive_delegatesToTheOrderedActiveOnlyQuery() {
    Country poland = countryWithCode("PL");
    when(countryRepository.findByActiveTrueOrderByDisplayOrderAsc()).thenReturn(List.of(poland));

    List<Country> result = service.listActive();

    assertThat(result).containsExactly(poland);
  }

  @Test
  void getByCode_unknownCode_throwsCountryNotFoundWithTheCodeInTheMessage() {
    when(countryRepository.findByCodeIgnoreCase("zz")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getByCode("zz"))
        .isInstanceOf(CountryNotFoundException.class)
        .isInstanceOf(ApiException.class)
        .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo("COUNTRY_NOT_FOUND"));
  }

  @Test
  void getByCode_knownCode_returnsIt() {
    Country poland = countryWithCode("PL");
    when(countryRepository.findByCodeIgnoreCase("PL")).thenReturn(Optional.of(poland));

    assertThat(service.getByCode("PL")).isEqualTo(poland);
  }

  private Country countryWithCode(String code) {
    Country country = new Country();
    ReflectionTestUtils.setField(country, "code", code);
    ReflectionTestUtils.setField(country, "canonicalName", "Test Country " + code);
    return country;
  }
}
