package com.foreignerwarsaw.reference.country;

import java.util.List;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Plain lookup only - classification lives in {@link CountryClassificationService} (brief §20:
 * don't build one giant reference-data service).
 */
@Service
public class CountryService {

  private final CountryRepository countryRepository;

  public CountryService(CountryRepository countryRepository) {
    this.countryRepository = countryRepository;
  }

  /**
   * Cached (brief §34): this changes on the order of "a new country is added to the seed data," not
   * per-request - an in-memory cache is enough, no Redis needed for stable public reference data
   * this size (250 rows).
   */
  @Cacheable("activeCountries")
  @Transactional(readOnly = true)
  public List<Country> listActive() {
    return countryRepository.findByActiveTrueOrderByDisplayOrderAsc();
  }

  @Transactional(readOnly = true)
  public Country getByCode(String code) {
    return countryRepository
        .findByCodeIgnoreCase(code)
        .orElseThrow(() -> new CountryNotFoundException(code));
  }
}
