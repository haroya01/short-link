package com.example.short_link.campaign.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidCampaignPeriodException extends RuntimeException {
  public InvalidCampaignPeriodException() {
    super("endsAt must be after startsAt");
  }
}
