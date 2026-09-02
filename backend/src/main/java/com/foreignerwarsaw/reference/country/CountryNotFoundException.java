package com.foreignerwarsaw.reference.country;

import com.foreignerwarsaw.common.web.ApiException;
import org.springframework.http.HttpStatus;

public class CountryNotFoundException extends ApiException {

  public CountryNotFoundException(String code) {
    super(HttpStatus.NOT_FOUND, "COUNTRY_NOT_FOUND", "No country found for code '" + code + "'");
  }
}
