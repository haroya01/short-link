package com.example.short_link.link.application.dto;

import com.example.short_link.link.domain.ShortCode;

public record LinkCreated(ShortCode shortCode, String claimToken) {

  public LinkCreated(ShortCode shortCode) {
    this(shortCode, null);
  }
}
