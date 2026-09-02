package com.foreignerwarsaw.reference.authority;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Named {@code OfficeLookupService}, not {@code OfficeService} - that name is taken by the
 * office/service-type join entity (brief §14). Reference/routing lookup only (brief §31) - no
 * procedure-recommendation logic belongs here.
 */
@Service
public class OfficeLookupService {

  private final OfficeRepository officeRepository;
  private final OfficeServiceRepository officeServiceRepository;

  public OfficeLookupService(
      OfficeRepository officeRepository, OfficeServiceRepository officeServiceRepository) {
    this.officeRepository = officeRepository;
    this.officeServiceRepository = officeServiceRepository;
  }

  @Transactional(readOnly = true)
  public List<Office> search(
      String cityCode, String districtCode, String authorityCode, String serviceCode) {
    return officeRepository.search(cityCode, districtCode, authorityCode, serviceCode);
  }

  @Transactional(readOnly = true)
  public List<String> serviceCodesFor(Office office) {
    return officeServiceRepository.findByOffice_CodeAndActiveTrue(office.getCode()).stream()
        .map(os -> os.getServiceType().getCode())
        .toList();
  }
}
