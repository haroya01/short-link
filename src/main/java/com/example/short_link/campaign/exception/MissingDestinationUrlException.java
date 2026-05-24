package com.example.short_link.campaign.exception;

import org.springframework.http.HttpStatus;

public final class MissingDestinationUrlException extends CampaignException {

  public MissingDestinationUrlException() {
    super("destinationUrl is required when campaign default destination is not set");
  }

  @Override
  public HttpStatus status() {
    return HttpStatus.BAD_REQUEST;
  }

  @Override
  public String code() {
    return "MISSING_DESTINATION_URL";
  }
}
