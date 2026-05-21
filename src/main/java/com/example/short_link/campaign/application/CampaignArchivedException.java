package com.example.short_link.campaign.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class CampaignArchivedException extends RuntimeException {
  public CampaignArchivedException() {
    super("campaign is archived");
  }
}
