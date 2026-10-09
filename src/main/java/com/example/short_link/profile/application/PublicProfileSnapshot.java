package com.example.short_link.profile.application;

import com.example.short_link.link.domain.ShortCode;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

// The profile is cached for a day, so links that open or expire are hidden when it is served, not
// when it is cached.
public record PublicProfileSnapshot(PublicProfile profile, List<LinkWindow> linkWindows) {

  public PublicProfile visibleAt(Instant now) {
    Set<ShortCode> hidden =
        linkWindows.stream()
            .filter(window -> window.hidesAt(now))
            .map(LinkWindow::shortCode)
            .collect(Collectors.toSet());
    if (hidden.isEmpty()) return profile;
    return profile.withEntries(
        profile.entries().stream()
            .filter(entry -> entry.shortCode() == null || !hidden.contains(entry.shortCode()))
            .toList());
  }

  public record LinkWindow(ShortCode shortCode, Instant opensAt, Instant expiresAt) {

    boolean hidesAt(Instant now) {
      return (opensAt != null && now.isBefore(opensAt))
          || (expiresAt != null && !now.isBefore(expiresAt));
    }
  }
}
