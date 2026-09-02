package com.foreignerwarsaw.reference.geography;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Country -> Region -> City -> District traversal only (brief §20) - classification and
 * jurisdiction/authority concerns live in their own services.
 */
@Service
public class GeographyService {

  private final RegionRepository regionRepository;
  private final CityRepository cityRepository;
  private final DistrictRepository districtRepository;

  public GeographyService(
      RegionRepository regionRepository,
      CityRepository cityRepository,
      DistrictRepository districtRepository) {
    this.regionRepository = regionRepository;
    this.cityRepository = cityRepository;
    this.districtRepository = districtRepository;
  }

  @Transactional(readOnly = true)
  public List<Region> regionsForCountry(String countryCode) {
    return regionRepository.findByCountry_CodeIgnoreCaseAndActiveTrueOrderByCanonicalNameAsc(
        countryCode);
  }

  @Transactional(readOnly = true)
  public List<City> citiesForRegion(String regionCode) {
    return cityRepository.findByRegion_CodeIgnoreCaseAndActiveTrueOrderByCanonicalNameAsc(
        regionCode);
  }

  @Transactional(readOnly = true)
  public List<District> districtsForCity(String cityCode) {
    return districtRepository.findByCity_CodeIgnoreCaseAndActiveTrueOrderByCanonicalNameAsc(
        cityCode);
  }
}
