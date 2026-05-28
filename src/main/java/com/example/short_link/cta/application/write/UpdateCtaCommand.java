package com.example.short_link.cta.application.write;

import com.example.short_link.cta.domain.CtaPurpose;
import com.example.short_link.cta.domain.CtaStyle;

/** PATCH 의미. null = 변경 안 함. */
public record UpdateCtaCommand(
    Long userId, Long ctaId, String label, String url, CtaStyle style, CtaPurpose purpose) {

  public UpdateCtaCommand {
    if (userId == null) throw new IllegalArgumentException("userId required");
    if (ctaId == null) throw new IllegalArgumentException("ctaId required");
    if (label != null) {
      if (label.isBlank()) throw new IllegalArgumentException("label cannot be blank");
      if (label.length() > 100) throw new IllegalArgumentException("label max 100");
    }
    if (url != null) {
      if (url.isBlank()) throw new IllegalArgumentException("url cannot be blank");
      if (url.length() > 2048) throw new IllegalArgumentException("url max 2048");
      if (!url.startsWith("http://") && !url.startsWith("https://")) {
        throw new IllegalArgumentException("url must start with http:// or https://");
      }
    }
  }
}
