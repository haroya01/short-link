package com.example.short_link.campaign.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class CampaignNotFoundException extends RuntimeException {
  public CampaignNotFoundException() {
    super("campaign not found");
  }
}
