package com.example.short_link.campaign.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

// 소유자 아닌 사용자에겐 존재 자체를 노출하지 않으려 404 로 통일.
@ResponseStatus(HttpStatus.NOT_FOUND)
public class CampaignNotOwnedException extends RuntimeException {
  public CampaignNotOwnedException() {
    super("campaign not owned");
  }
}
