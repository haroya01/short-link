package com.example.short_link.campaign.exception;

import org.springframework.http.HttpStatus;

public final class InvalidCampaignPeriodException extends CampaignException {

  public InvalidCampaignPeriodException() {
    super("endsAt must be after startsAt");
  }

  @Override
  public HttpStatus status() {
    return HttpStatus.BAD_REQUEST;
  }

  @Override
  public String code() {
    return "INVALID_CAMPAIGN_PERIOD";
  }
}
