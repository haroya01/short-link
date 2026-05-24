package com.example.short_link.profile.exception;

import org.springframework.http.HttpStatus;

public final class OembedNotApplicableException extends ProfileException {

  public OembedNotApplicableException() {
    super("oembed not applicable");
  }

  @Override
  public HttpStatus status() {
    return HttpStatus.UNPROCESSABLE_ENTITY;
  }

  @Override
  public String code() {
    return "OEMBED_NOT_APPLICABLE";
  }
}
