package com.example.short_link.link.stats.application.read;

import com.example.short_link.link.application.dto.LinkStats;
import com.example.short_link.link.domain.LinkId;
import com.example.short_link.link.stats.domain.repository.ClickEventRepository;
import com.example.short_link.link.stats.domain.repository.ClickTotalsReadRepository;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class LinkStatsTotalsReader {

  private static final Duration BASELINE_WINDOW = Duration.ofHours(24);

  private final ClickEventRepository clickRepository;
  private final ClickTotalsReadRepository clickTotals;

  Totals totals(LinkId linkId, Instant linkCreatedAt) {
    long total = clickRepository.countByLinkId(linkId.value());
    long human = clickTotals.countHumanByLinkId(linkId.value());
    long bot = clickTotals.countBotByLinkId(linkId.value());
    long unique = clickTotals.countUniqueVisitorsByLinkId(linkId.value());
    Instant firstClickAt = clickTotals.findFirstClickAt(linkId.value());
    Instant lastClickAt = clickTotals.findLastClickAt(linkId.value());
    long previewClicks = clickTotals.countPreviewByLinkId(linkId.value());
    long profileClicks = clickTotals.countProfileChannelByLinkId(linkId.value());
    Long timeToFirstClickMinutes =
        firstClickAt == null
            ? null
            : Math.max(0, Duration.between(linkCreatedAt, firstClickAt).toMinutes());
    return new Totals(
        total,
        human,
        bot,
        unique,
        previewClicks,
        profileClicks,
        firstClickAt,
        lastClickAt,
        timeToFirstClickMinutes);
  }

  LinkStats.Velocity velocity(LinkId linkId) {
    Instant now = Instant.now();
    long currentHour =
        clickTotals.countSinceByLinkId(linkId.value(), now.minus(Duration.ofHours(1)));
    long last24h = clickTotals.countSinceByLinkId(linkId.value(), now.minus(BASELINE_WINDOW));
    double baseline = last24h / 24.0;
    double ratio = baseline > 0 ? currentHour / baseline : 0.0;
    return new LinkStats.Velocity(currentHour, baseline, ratio);
  }

  record Totals(
      long total,
      long human,
      long bot,
      long unique,
      long previewClicks,
      long profileClicks,
      Instant firstClickAt,
      Instant lastClickAt,
      Long timeToFirstClickMinutes) {}
}
