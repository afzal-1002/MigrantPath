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
class OfficeLookupServiceTest {

  @Mock private OfficeRepository officeRepository;
  @Mock private OfficeServiceRepository officeServiceRepository;

  private OfficeLookupService service;

  @BeforeEach
  void setUp() {
    service = new OfficeLookupService(officeRepository, officeServiceRepository);
  }

  @Test
  void search_delegatesAllFourFiltersStraightThroughToTheRepository() {
    Office office = officeWithCode("MAZOWIECKIE_WSC_MARSZALKOWSKA");
    when(officeRepository.search("WARSAW", "SRODMIESCIE", "UDSC", "PESEL"))
        .thenReturn(List.of(office));

    assertThat(service.search("WARSAW", "SRODMIESCIE", "UDSC", "PESEL")).containsExactly(office);
  }

  @Test
  void serviceCodesFor_returnsOnlyActiveServiceTypeCodesForThatOffice() {
    Office office = officeWithCode("MAZOWIECKIE_WSC_MARSZALKOWSKA");
    OfficeService immigrationInfo = officeServiceFor("IMMIGRATION_INFORMATION");
    when(officeServiceRepository.findByOffice_CodeAndActiveTrue("MAZOWIECKIE_WSC_MARSZALKOWSKA"))
        .thenReturn(List.of(immigrationInfo));

    assertThat(service.serviceCodesFor(office)).containsExactly("IMMIGRATION_INFORMATION");
  }

  @Test
  void serviceCodesFor_officeWithNoServices_returnsEmptyList() {
    Office office = officeWithCode("SOME_OTHER_OFFICE");
    when(officeServiceRepository.findByOffice_CodeAndActiveTrue("SOME_OTHER_OFFICE"))
        .thenReturn(List.of());

    assertThat(service.serviceCodesFor(office)).isEmpty();
  }

  private Office officeWithCode(String code) {
    Office office = new Office();
    ReflectionTestUtils.setField(office, "code", code);
    return office;
  }

  private OfficeService officeServiceFor(String serviceTypeCode) {
    ServiceType serviceType = new ServiceType();
    ReflectionTestUtils.setField(serviceType, "code", serviceTypeCode);
    OfficeService officeService = new OfficeService();
    ReflectionTestUtils.setField(officeService, "serviceType", serviceType);
    return officeService;
  }
}
