package com.example.short_link.campaign.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class CampaignBatchNotFoundException extends RuntimeException {
  public CampaignBatchNotFoundException() {
    super("batch not found");
  }
}
