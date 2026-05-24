package com.example.short_link.campaign.exception;

import org.springframework.http.HttpStatus;

/** 소유자 아닌 사용자에겐 존재 자체를 노출하지 않으려 404 + CAMPAIGN_NOT_FOUND 로 통일. */
public final class CampaignNotOwnedException extends CampaignException {

  public CampaignNotOwnedException() {
    super("campaign not owned");
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
