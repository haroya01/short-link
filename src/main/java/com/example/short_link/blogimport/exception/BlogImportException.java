package com.example.short_link.blogimport.exception;

import com.example.short_link.common.exception.DomainException;
import org.springframework.http.HttpStatus;

public final class BlogImportException extends RuntimeException implements DomainException {

  private final BlogImportErrorCode errorCode;

  public BlogImportException(BlogImportErrorCode errorCode, Object... messageArgs) {
    super(errorCode.format(messageArgs));
    this.errorCode = errorCode;
  }

  public BlogImportErrorCode errorCode() {
    return errorCode;
  }

  @Override
  public HttpStatus status() {
    return errorCode.status();
  }

  @Override
  public String code() {
    return errorCode.name();
  }
}
