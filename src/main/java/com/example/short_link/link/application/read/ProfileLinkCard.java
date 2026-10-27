package com.example.short_link.link.application.read;

import com.example.short_link.link.domain.ShortCode;
import java.time.Instant;

public record ProfileLinkCard(
    ShortCode shortCode,
    String url,
    String originalUrl,
    Preview preview,
    boolean passwordRequired,
    boolean highlighted,
    Integer profileOrder,
    long clicks,
    Window window) {

  public record Preview(String title, String image, String titleOverride) {}

  public record Window(Instant opensAt, Instant expiresAt, boolean viewLimited) {

    public boolean limits() {
      return opensAt != null || expiresAt != null || viewLimited;
    }
  }
}
