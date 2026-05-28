package com.example.short_link.link.og.presentation.response;

import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.og.application.dto.OgOverrideResult;

public record OgOverrideResponse(
    ShortCode shortCode, String ogTitle, String ogDescription, String ogImage) {

  public static OgOverrideResponse from(OgOverrideResult result) {
    return new OgOverrideResponse(
        result.shortCode(), result.ogTitle(), result.ogDescription(), result.ogImage());
  }
}
