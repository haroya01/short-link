package com.example.short_link.campaign.exception;

import org.springframework.http.HttpStatus;

public enum CampaignErrorCode {
  CAMPAIGN_NOT_FOUND(HttpStatus.NOT_FOUND, "campaign not found"),
  CAMPAIGN_BATCH_NOT_FOUND(HttpStatus.NOT_FOUND, "batch not found"),
  CAMPAIGN_ARCHIVED(HttpStatus.BAD_REQUEST, "campaign is archived"),
  CAMPAIGN_TERMINAL_STATE(HttpStatus.BAD_REQUEST, "campaign is ended or archived"),
  INVALID_CAMPAIGN_PERIOD(HttpStatus.BAD_REQUEST, "endsAt must be after startsAt"),
  MISSING_DESTINATION_URL(
      HttpStatus.BAD_REQUEST,
      "destinationUrl is required when campaign default destination is not set"),
  MISSING_POST_END_DESTINATION(
      HttpStatus.BAD_REQUEST, "postEndDestinationUrl is required when postEndAction is REDIRECT"),
  REAPPLY_ON_NON_ENDED(HttpStatus.BAD_REQUEST, "policy can only be re-applied to ENDED campaigns"),
  INVALID_BATCH_ROW(HttpStatus.BAD_REQUEST, "row %d: %s");

  private final HttpStatus status;
  private final String template;

  CampaignErrorCode(HttpStatus status, String template) {
    this.status = status;
    this.template = template;
  }

  public HttpStatus status() {
    return status;
  }

  public String format(Object... args) {
    return args == null || args.length == 0 ? template : template.formatted(args);
  }

  public CampaignException raise(Object... args) {
    return new CampaignException(this, args);
  }
}
