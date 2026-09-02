package com.foreignerwarsaw.reference.authority.dto;

import com.foreignerwarsaw.reference.authority.Office;
import java.util.List;

public record OfficeResponse(
    String code,
    String authorityCode,
    String name,
    String street,
    String buildingNumber,
    String postalCode,
    String cityCode,
    String districtCode,
    String phone,
    String email,
    String website,
    Boolean appointmentRequired,
    String bookingUrl,
    List<String> services) {

  public static OfficeResponse of(Office office, List<String> services) {
    return new OfficeResponse(
        office.getCode(),
        office.getAuthority().getCode(),
        office.getCanonicalName(),
        office.getStreet(),
        office.getBuildingNumber(),
        office.getPostalCode(),
        office.getCity().getCode(),
        office.getDistrict() != null ? office.getDistrict().getCode() : null,
        office.getPhone(),
        office.getEmail(),
        office.getWebsite(),
        office.getAppointmentRequired(),
        office.getBookingUrl(),
        services);
  }
}
