package com.example.short_link.campaign.exception;

import org.springframework.http.HttpStatus;

public final class CampaignBatchNotFoundException extends CampaignException {

  public CampaignBatchNotFoundException() {
    super("batch not found");
  }

  @Override
  public HttpStatus status() {
    return HttpStatus.NOT_FOUND;
  }

  @Override
  public String code() {
    return "CAMPAIGN_BATCH_NOT_FOUND";
  }
}
