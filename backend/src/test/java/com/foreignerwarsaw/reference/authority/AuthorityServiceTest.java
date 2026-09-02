package com.foreignerwarsaw.reference.authority;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AuthorityServiceTest {

  @Mock private AuthorityRepository authorityRepository;

  private AuthorityService service;

  @BeforeEach
  void setUp() {
    service = new AuthorityService(authorityRepository);
  }

  @Test
  void search_delegatesAllThreeFiltersStraightThroughToTheRepository() {
    Authority udsc = authorityWithCode("UDSC");
    when(authorityRepository.search("PL", "WARSAW", "NATIONAL_AGENCY")).thenReturn(List.of(udsc));

    assertThat(service.search("PL", "WARSAW", "NATIONAL_AGENCY")).containsExactly(udsc);
  }

  @Test
  void search_noFilters_returnsEverythingTheRepositoryReturns() {
    Authority udsc = authorityWithCode("UDSC");
    Authority cityHall = authorityWithCode("WARSAW_CITY_HALL");
    when(authorityRepository.search(null, null, null)).thenReturn(List.of(udsc, cityHall));

    assertThat(service.search(null, null, null)).containsExactly(udsc, cityHall);
  }

  private Authority authorityWithCode(String code) {
    Authority authority = new Authority();
    ReflectionTestUtils.setField(authority, "code", code);
    return authority;
  }
}
