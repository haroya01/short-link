package com.example.short_link.link.safety.application;

import com.example.short_link.link.destination.domain.LinkDestinationEntity;
import com.example.short_link.link.destination.domain.repository.LinkDestinationRepository;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.domain.repository.LinkRepository.SafetyRescanRow;
import com.example.short_link.link.moderation.application.LinkModerationService;
import com.example.short_link.link.moderation.domain.LinkDisableReason;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LinkSafetyRescanner {

  // threatMatches:find accepts at most 500 threat entries per request.
  static final int LOOKUP_BATCH = 500;

  private final LinkRepository links;
  private final LinkDestinationRepository destinations;
  private final UrlThreatLookup lookup;
  private final LinkModerationService moderation;

  public record Result(int scanned, int disabled, long lastLinkId, boolean reachedEnd) {}

  public Result rescan(long afterId, int limit, Instant now) {
    List<SafetyRescanRow> batch = links.findSafetyRescanBatch(afterId, now, limit);
    if (batch.isEmpty()) {
      return new Result(0, 0, afterId, true);
    }
    Map<Long, Set<String>> urlsByLink = new LinkedHashMap<>();
    for (SafetyRescanRow row : batch) {
      urlsByLink
          .computeIfAbsent(row.getLinkId(), id -> new LinkedHashSet<>())
          .add(row.getOriginalUrl());
    }
    for (LinkDestinationEntity variant : destinations.findAllByLinkIdIn(urlsByLink.keySet())) {
      if (variant.isEnabled()) {
        urlsByLink.get(variant.getLinkId()).add(variant.getUrl());
      }
    }
    List<String> urls = urlsByLink.values().stream().flatMap(Set::stream).distinct().toList();
    Set<String> unsafe = new HashSet<>();
    for (int from = 0; from < urls.size(); from += LOOKUP_BATCH) {
      unsafe.addAll(
          lookup.unsafeAmong(urls.subList(from, Math.min(urls.size(), from + LOOKUP_BATCH))));
    }
    int disabled = 0;
    for (Map.Entry<Long, Set<String>> link : urlsByLink.entrySet()) {
      if (link.getValue().stream().anyMatch(unsafe::contains)
          && moderation.disable(link.getKey(), LinkDisableReason.SAFE_BROWSING, null)) {
        disabled++;
      }
    }
    long last = batch.get(batch.size() - 1).getLinkId();
    return new Result(batch.size(), disabled, last, batch.size() < limit);
  }
}
