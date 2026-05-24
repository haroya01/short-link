package com.example.short_link.campaign.exception;

import org.springframework.http.HttpStatus;

public final class CampaignArchivedException extends CampaignException {

  public CampaignArchivedException() {
    super("campaign is archived");
  }

  @Override
  public HttpStatus status() {
    return HttpStatus.BAD_REQUEST;
  }

  @Override
  public String code() {
    return "CAMPAIGN_ARCHIVED";
  }
}
