package com.example.short_link.link.webhook.application.helper;

import com.example.short_link.link.domain.LinkId;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.stats.domain.repository.ClickAlertReadRepository;
import com.example.short_link.link.stats.domain.repository.ClickRangeReadRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DailySummaryAssembler {

  private final ClickRangeReadRepository clickRanges;
  private final ClickAlertReadRepository clickAlerts;

  public DailySummaryPayload assemble(
      LinkId linkId, ShortCode shortCode, LocalDate localDay, ZoneId tz) {
    Instant windowStart = localDay.atStartOfDay(tz).toInstant();
    Instant windowEnd = localDay.plusDays(1).atStartOfDay(tz).toInstant();
    Instant prevStart = localDay.minusDays(1).atStartOfDay(tz).toInstant();
    Instant sevenDayStart = localDay.minusDays(7).atStartOfDay(tz).toInstant();

    long human = clickRanges.countHumanByLinkIdAndRange(linkId.value(), windowStart, windowEnd);
    long bot = clickRanges.countBotByLinkIdAndRange(linkId.value(), windowStart, windowEnd);
    long unique =
        clickRanges.countUniqueVisitorsByLinkIdAndRange(linkId.value(), windowStart, windowEnd);
    long total = human + bot;

    long prevHuman = clickRanges.countHumanByLinkIdAndRange(linkId.value(), prevStart, windowStart);
    long sevenDayHuman =
        clickRanges.countHumanByLinkIdAndRange(linkId.value(), sevenDayStart, windowStart);

    String topChannel =
        clickAlerts
            .findTopChannelByLinkIdAndRange(linkId.value(), windowStart, windowEnd)
            .map(r -> r.getSource())
            .orElse(null);
    String topCountry =
        clickAlerts
            .findTopCountryByLinkIdAndRange(linkId.value(), windowStart, windowEnd)
            .map(r -> r.getCountry())
            .orElse(null);
    String topDevice =
        clickAlerts
            .findTopDeviceByLinkIdAndRange(linkId.value(), windowStart, windowEnd)
            .map(r -> r.getDevice())
            .orElse(null);

    var peakRow =
        clickAlerts.findPeakHourByLinkIdAndRange(
            linkId.value(), windowStart, windowEnd, tz.getId());
    int peakHour = peakRow.map(r -> r.getHour()).orElse(0);
    long peakHourClicks = peakRow.map(r -> r.getCount()).orElse(0L);

    return new DailySummaryPayload(
        shortCode,
        localDay.atStartOfDay().toString(),
        LocalDateTime.of(localDay, LocalTime.MAX).withNano(0).toString(),
        total,
        human,
        bot,
        unique,
        topChannel,
        topCountry,
        topDevice,
        peakHour,
        peakHourClicks,
        ratio(human, prevHuman),
        ratio(human, sevenDayHuman / 7.0));
  }

  static Double ratio(long current, double baseline) {
    if (baseline == 0) return null;
    return (current - baseline) / baseline;
  }

  static Double ratio(long current, long baseline) {
    return ratio(current, (double) baseline);
  }
}
