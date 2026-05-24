package com.example.short_link.campaign.exception;

import org.springframework.http.HttpStatus;

public final class ReapplyOnNonEndedException extends CampaignException {

  public ReapplyOnNonEndedException() {
    super("policy can only be re-applied to ENDED campaigns");
  }

  @Override
  public HttpStatus status() {
    return HttpStatus.BAD_REQUEST;
  }

  @Override
  public String code() {
    return "REAPPLY_ON_NON_ENDED";
  }
}
