package com.example.short_link.link.application;

import com.example.short_link.link.domain.ClickEventEntity;
import com.example.short_link.link.domain.ClickEventRepository;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.LinkRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LinkExportService {

  private static final int EVENT_BATCH = 1000;
  private static final int EVENT_HARD_CAP = 50_000;

  private final LinkRepository linkRepository;
  private final ClickEventRepository clickRepository;
  private final ReferrerChannelClassifier channelClassifier;
  private final LinkStatsService statsService;

  @Transactional(readOnly = true)
  public String exportEventsCsv(Long userId, String shortCode) {
    LinkEntity link =
        linkRepository
            .findByShortCode(shortCode)
            .orElseThrow(() -> new LinkNotFoundException(shortCode));
    if (!link.isOwnedBy(userId)) {
      throw new LinkNotOwnedException(shortCode);
    }
    StringBuilder sb = new StringBuilder(64 * 1024);
    CsvWriter.appendRow(
        sb,
        "clicked_at",
        "country",
        "region",
        "city",
        "device",
        "os",
        "browser",
        "channel",
        "referrer_host",
        "language",
        "is_bot",
        "bot_name");

    int written = 0;
    Long cursorId = null;
    PageRequest req = PageRequest.ofSize(EVENT_BATCH);
    while (written < EVENT_HARD_CAP) {
      List<ClickEventEntity> rows;
      if (cursorId == null) {
        rows = clickRepository.findEventsByLinkIdLatest(link.getId(), req);
      } else {
        rows = clickRepository.findEventsByLinkIdBefore(link.getId(), cursorId, req);
      }
      if (rows.isEmpty()) break;
      for (ClickEventEntity c : rows) {
        if (written >= EVENT_HARD_CAP) break;
        String channel =
            c.getReferrer() == null ? "direct" : channelClassifier.classify(c.getReferrer());
        CsvWriter.appendRow(
            sb,
            c.getClickedAt(),
            c.getCountryCode(),
            c.getRegionName(),
            c.getCityName(),
            c.getDeviceClass(),
            c.getOsName(),
            c.getBrowserName(),
            channel,
            c.getReferrerHost(),
            c.getLanguage(),
            c.isBot(),
            c.getBotName());
        written++;
      }
      ClickEventEntity last = rows.get(rows.size() - 1);
      cursorId = last.getId();
      if (rows.size() < EVENT_BATCH) break;
    }
    return sb.toString();
  }

  @Transactional(readOnly = true)
  public String exportStatsCsv(Long userId, String shortCode, String dimension) {
    LinkStats stats = statsService.stats(userId, shortCode);
    StringBuilder sb = new StringBuilder(8 * 1024);
    String dim = dimension == null ? "daily" : dimension.toLowerCase();
    switch (dim) {
      case "daily" -> {
        CsvWriter.appendRow(sb, "date", "count");
        stats.dailyClicks().forEach(d -> CsvWriter.appendRow(sb, d.date(), d.count()));
      }
      case "hourly" -> {
        CsvWriter.appendRow(sb, "hour", "count");
        stats.hourClicks().forEach(d -> CsvWriter.appendRow(sb, d.hour(), d.count()));
      }
      case "country" -> {
        CsvWriter.appendRow(sb, "country", "count");
        stats.countryClicks().forEach(d -> CsvWriter.appendRow(sb, d.country(), d.count()));
      }
      case "city" -> {
        CsvWriter.appendRow(sb, "city", "count");
        stats.cityClicks().forEach(d -> CsvWriter.appendRow(sb, d.city(), d.count()));
      }
      case "device" -> {
        CsvWriter.appendRow(sb, "device", "count");
        stats.deviceClicks().forEach(d -> CsvWriter.appendRow(sb, d.device(), d.count()));
      }
      case "os" -> {
        CsvWriter.appendRow(sb, "os", "count");
        stats.osClicks().forEach(d -> CsvWriter.appendRow(sb, d.os(), d.count()));
      }
      case "browser" -> {
        CsvWriter.appendRow(sb, "browser", "count");
        stats.browserClicks().forEach(d -> CsvWriter.appendRow(sb, d.browser(), d.count()));
      }
      case "referrer" -> {
        CsvWriter.appendRow(sb, "referrer", "count");
        stats.referrerClicks().forEach(d -> CsvWriter.appendRow(sb, d.referrer(), d.count()));
      }
      case "channel" -> {
        CsvWriter.appendRow(sb, "channel", "count");
        stats.channelClicks().forEach(d -> CsvWriter.appendRow(sb, d.channel(), d.count()));
      }
      default -> throw new InvalidExportDimensionException(dim);
    }
    return sb.toString();
  }
}
