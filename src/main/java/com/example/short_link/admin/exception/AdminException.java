package com.example.short_link.admin.exception;

import com.example.short_link.common.exception.DomainException;

public abstract sealed class AdminException extends RuntimeException implements DomainException
    permits InvalidActivePeriodException {

  protected AdminException(String message) {
    super(message);
  }
}
