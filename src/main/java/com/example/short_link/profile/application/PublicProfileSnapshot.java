package com.example.short_link.profile.application;

import com.example.short_link.link.domain.ShortCode;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

// The profile is cached for a day, so links that open, expire or spend their last view are hidden
// when it is served, not when it is cached.
public record PublicProfileSnapshot(PublicProfile profile, List<LinkWindow> linkWindows) {

  public Set<ShortCode> viewLimitedLinks() {
    return linkWindows.stream()
        .filter(LinkWindow::viewLimited)
        .map(LinkWindow::shortCode)
        .collect(Collectors.toSet());
  }

  public PublicProfile visibleAt(Instant now, Set<ShortCode> viewLimitReached) {
    Set<ShortCode> hidden = new HashSet<>(viewLimitReached);
    linkWindows.stream()
        .filter(window -> window.hidesAt(now))
        .map(LinkWindow::shortCode)
        .forEach(hidden::add);
    if (hidden.isEmpty()) return profile;
    return profile.withEntries(
        profile.entries().stream()
            .filter(entry -> entry.shortCode() == null || !hidden.contains(entry.shortCode()))
            .toList());
  }

  public record LinkWindow(
      ShortCode shortCode, Instant opensAt, Instant expiresAt, boolean viewLimited) {

    boolean hidesAt(Instant now) {
      return (opensAt != null && now.isBefore(opensAt))
          || (expiresAt != null && !now.isBefore(expiresAt));
    }
  }
}
