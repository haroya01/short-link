package com.example.short_link.campaign.exception;

import org.springframework.http.HttpStatus;

public final class CampaignTerminalStateException extends CampaignException {

  public CampaignTerminalStateException() {
    super("campaign is ended or archived");
  }

  @Override
  public HttpStatus status() {
    return HttpStatus.BAD_REQUEST;
  }

  @Override
  public String code() {
    return "CAMPAIGN_TERMINAL_STATE";
  }
}
