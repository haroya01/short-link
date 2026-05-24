package com.example.short_link.campaign.exception;

import org.springframework.http.HttpStatus;

public final class InvalidBatchRowException extends CampaignException {

  public InvalidBatchRowException(int rowIndex, String reason) {
    super("row " + rowIndex + ": " + reason);
  }

  @Override
  public HttpStatus status() {
    return HttpStatus.BAD_REQUEST;
  }

  @Override
  public String code() {
    return "INVALID_BATCH_ROW";
  }
}
