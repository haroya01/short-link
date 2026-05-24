package com.example.short_link.campaign.exception;

import org.springframework.http.HttpStatus;

public final class CampaignNotFoundException extends CampaignException {

  public CampaignNotFoundException() {
    super("campaign not found");
  }

  @Override
  public HttpStatus status() {
    return HttpStatus.NOT_FOUND;
  }

  @Override
  public String code() {
    return "CAMPAIGN_NOT_FOUND";
  }
}
