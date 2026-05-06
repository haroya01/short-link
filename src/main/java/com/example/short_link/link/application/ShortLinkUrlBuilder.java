package com.example.short_link.link.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ShortLinkUrlBuilder {

  private final String baseUrl;

  public ShortLinkUrlBuilder(@Value("${short-link.base-url}") String baseUrl) {
    this.baseUrl = baseUrl;
  }

  public String build(String shortCode) {
    return baseUrl + "/" + shortCode;
  }
}
