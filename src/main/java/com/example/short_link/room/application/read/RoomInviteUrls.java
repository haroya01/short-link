package com.example.short_link.room.application.read;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RoomInviteUrls {

  private final String baseUrl;
  private final String frontendBaseUrl;

  public RoomInviteUrls(
      @Value("${short-link.base-url}") String baseUrl,
      @Value("${short-link.frontend-base-url}") String frontendBaseUrl) {
    this.baseUrl = baseUrl;
    this.frontendBaseUrl = frontendBaseUrl;
  }

  public String page(String code) {
    return frontendBaseUrl + "/rooms/join/" + code;
  }

  public String shortLink(String shortCode) {
    return baseUrl + "/" + shortCode;
  }
}
