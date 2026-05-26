package com.example.short_link.common.pow;

import com.example.short_link.common.exception.DomainException;
import org.springframework.http.HttpStatus;

public class PowRequiredException extends RuntimeException implements DomainException {

  public PowRequiredException() {
    super("proof-of-work required for anonymous shorten");
  }

  @Override
  public HttpStatus status() {
    return HttpStatus.UNAUTHORIZED;
  }

  @Override
  public String code() {
    return "POW_REQUIRED";
  }
}
