package com.example.short_link.cta.application.read;

import com.example.short_link.cta.domain.CtaEntity;
import java.time.Instant;

public record CtaView(
    Long id,
    String label,
    String url,
    String style,
    String purpose,
    boolean deleted,
    Instant createdAt,
    Instant updatedAt) {

  public static CtaView from(CtaEntity cta) {
    return new CtaView(
        cta.getId(),
        cta.getLabel(),
        cta.getUrl(),
        cta.getStyle().name(),
        cta.getPurpose().name(),
        cta.isDeleted(),
        cta.getCreatedAt(),
        cta.getUpdatedAt());
  }
}
