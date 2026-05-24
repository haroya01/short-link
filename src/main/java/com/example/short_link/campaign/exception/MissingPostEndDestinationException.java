package com.example.short_link.campaign.exception;

import org.springframework.http.HttpStatus;

public final class MissingPostEndDestinationException extends CampaignException {

  public MissingPostEndDestinationException() {
    super("postEndDestinationUrl is required when postEndAction is REDIRECT");
  }

  @Override
  public HttpStatus status() {
    return HttpStatus.BAD_REQUEST;
  }

  @Override
  public String code() {
    return "MISSING_POST_END_DESTINATION";
  }
}
