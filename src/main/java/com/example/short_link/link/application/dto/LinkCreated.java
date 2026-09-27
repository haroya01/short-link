package com.example.short_link.link.application.dto;

import com.example.short_link.link.domain.ShortCode;

public record LinkCreated(ShortCode shortCode, String claimToken, boolean passwordProtected) {

  public LinkCreated(ShortCode shortCode) {
    this(shortCode, null, false);
  }

  public LinkCreated(ShortCode shortCode, String claimToken) {
    this(shortCode, claimToken, false);
  }
}
