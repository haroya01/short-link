package com.example.short_link.link.access.presentation.response;

import com.example.short_link.link.access.application.dto.LinkProtectionResult;
import com.example.short_link.link.domain.ShortCode;

public record LinkProtectionResponse(
    ShortCode shortCode, boolean passwordProtected, Integer maxViews, int viewCount) {

  public static LinkProtectionResponse from(LinkProtectionResult result) {
    return new LinkProtectionResponse(
        result.shortCode(), result.passwordProtected(), result.maxViews(), result.viewCount());
  }
}
