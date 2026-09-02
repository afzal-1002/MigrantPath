package com.foreignerwarsaw.reference.authority;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthorityService {

  private final AuthorityRepository authorityRepository;

  public AuthorityService(AuthorityRepository authorityRepository) {
    this.authorityRepository = authorityRepository;
  }

  @Transactional(readOnly = true)
  public List<Authority> search(String jurisdictionCode, String cityCode, String authorityType) {
    return authorityRepository.search(jurisdictionCode, cityCode, authorityType);
  }
}
