package com.example.short_link.common.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * Feature exceptions supply their response status and machine code so handlers do not need subtype
 * mapping tables.
 */
public interface DomainException {

  HttpStatus status();

  String code();

  default Map<String, Object> properties() {
    return Map.of();
  }
}
