package com.foreignerwarsaw.auth;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserConsentRepository extends JpaRepository<UserConsent, UUID> {

  /** Phase 12 personal-data export (brief §14/§16). */
  List<UserConsent> findByUser_IdOrderByAcceptedAtAsc(UUID userId);
}
