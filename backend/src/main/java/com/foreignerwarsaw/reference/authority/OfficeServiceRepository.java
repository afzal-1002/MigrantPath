package com.foreignerwarsaw.reference.authority;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OfficeServiceRepository extends JpaRepository<OfficeService, OfficeServiceId> {

  List<OfficeService> findByOffice_CodeAndActiveTrue(String officeCode);
}
