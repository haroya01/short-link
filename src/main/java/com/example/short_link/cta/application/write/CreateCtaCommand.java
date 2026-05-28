package com.example.short_link.cta.application.write;

import com.example.short_link.cta.domain.CtaPurpose;
import com.example.short_link.cta.domain.CtaStyle;

public record CreateCtaCommand(
    Long userId, String label, String url, CtaStyle style, CtaPurpose purpose) {

  public CreateCtaCommand {
    if (userId == null) throw new IllegalArgumentException("userId required");
    if (label == null || label.isBlank()) throw new IllegalArgumentException("label required");
    if (label.length() > 100) throw new IllegalArgumentException("label max 100");
    if (url == null || url.isBlank()) throw new IllegalArgumentException("url required");
    if (url.length() > 2048) throw new IllegalArgumentException("url max 2048");
    if (!url.startsWith("http://") && !url.startsWith("https://")) {
      throw new IllegalArgumentException("url must start with http:// or https://");
    }
    if (style == null) style = CtaStyle.PRIMARY;
    if (purpose == null) purpose = CtaPurpose.CUSTOM;
  }
}
