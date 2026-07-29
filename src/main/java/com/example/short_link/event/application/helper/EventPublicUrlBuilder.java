package com.example.short_link.event.application.helper;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 이벤트 공개 페이지 URL — {@code {frontend}/e/{slug}}. {@code PostPublicUrlBuilder} 와 같은 원칙. */
@Component
public class EventPublicUrlBuilder {

  private final String frontendBaseUrl;

  public EventPublicUrlBuilder(@Value("${short-link.frontend-base-url}") String frontendBaseUrl) {
    this.frontendBaseUrl = frontendBaseUrl;
  }

  public String build(String slug) {
    return frontendBaseUrl + "/e/" + slug;
  }
}
