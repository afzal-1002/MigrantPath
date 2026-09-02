package com.foreignerwarsaw.reference.authority;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

/**
 * Named {@code OfficeService} per this phase's brief §14 - not to be confused with an application
 * service class; see {@link OfficeLookupService} for that. Pure join row, composite natural key
 * (docs/database/DATABASE.md §0).
 */
@Entity
@Table(name = "office_services")
public class OfficeService {

  @EmbeddedId private OfficeServiceId id;

  @ManyToOne(fetch = FetchType.LAZY)
  @MapsId("officeId")
  @JoinColumn(name = "office_id")
  private Office office;

  @ManyToOne(fetch = FetchType.LAZY)
  @MapsId("serviceTypeId")
  @JoinColumn(name = "service_type_id")
  private ServiceType serviceType;

  private boolean active = true;

  protected OfficeService() {}

  public OfficeService(Office office, ServiceType serviceType) {
    this.office = office;
    this.serviceType = serviceType;
    this.id = new OfficeServiceId(office.getId(), serviceType.getId());
  }

  public Office getOffice() {
    return office;
  }

  public ServiceType getServiceType() {
    return serviceType;
  }

  public boolean isActive() {
    return active;
  }
}
