package com.foreignerwarsaw.reference.authority;

import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Composite key for {@link OfficeService} - a pure join row with no identity of its own beyond the
 * (office, service type) pair (docs/database/DATABASE.md §0's convention for join tables like this
 * one).
 */
@Embeddable
public class OfficeServiceId implements Serializable {

  private UUID officeId;
  private UUID serviceTypeId;

  protected OfficeServiceId() {}

  public OfficeServiceId(UUID officeId, UUID serviceTypeId) {
    this.officeId = officeId;
    this.serviceTypeId = serviceTypeId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof OfficeServiceId other)) return false;
    return Objects.equals(officeId, other.officeId)
        && Objects.equals(serviceTypeId, other.serviceTypeId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(officeId, serviceTypeId);
  }
}
