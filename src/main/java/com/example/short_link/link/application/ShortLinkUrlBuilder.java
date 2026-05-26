package com.example.short_link.link.application;

import com.example.short_link.link.domain.ShortCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ShortLinkUrlBuilder {

  private final String baseUrl;

  public ShortLinkUrlBuilder(@Value("${short-link.base-url}") String baseUrl) {
    this.baseUrl = baseUrl;
  }

  public String build(ShortCode shortCode) {
    return baseUrl + "/" + shortCode;
  }
}
