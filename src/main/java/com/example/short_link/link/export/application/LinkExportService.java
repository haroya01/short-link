package com.example.short_link.link.export.application;

import com.example.short_link.link.access.application.LinkAccessGuard;
import com.example.short_link.link.application.dto.LinkStats;
import com.example.short_link.link.classifier.application.ReferrerChannelClassifier;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.exception.LinkErrorCode;
import com.example.short_link.link.exception.LinkException;
import com.example.short_link.link.export.application.helper.CsvWriter;
import com.example.short_link.link.stats.application.LinkClickEventReader;
import com.example.short_link.link.stats.application.read.LinkStatsQueryService;
import com.example.short_link.link.stats.domain.ClickEventEntity;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LinkExportService {

  private final LinkRepository linkRepository;
  private final LinkClickEventReader clickEvents;
  private final ReferrerChannelClassifier channelClassifier;
  private final LinkStatsQueryService statsService;
  private final LinkAccessGuard accessGuard;

  @org.springframework.beans.factory.annotation.Value("${short-link.export.event-batch:1000}")
  private int eventBatch;

  @org.springframework.beans.factory.annotation.Value("${short-link.export.event-cap:50000}")
  private int eventHardCap;

  @Transactional(readOnly = true)
  public String exportEventsCsv(Long userId, ShortCode shortCode) {
    LinkEntity link =
        linkRepository
            .findByShortCode(shortCode)
            .orElseThrow(() -> new LinkException(LinkErrorCode.LINK_NOT_FOUND, shortCode));
    accessGuard.requireView(userId, link);
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
    while (written < eventHardCap) {
      List<ClickEventEntity> rows;
      if (cursorId == null) {
        rows = clickEvents.latest(link.linkId().value(), eventBatch);
      } else {
        rows = clickEvents.before(link.linkId().value(), cursorId, eventBatch);
      }
      if (rows.isEmpty()) break;
      for (ClickEventEntity c : rows) {
        if (written >= eventHardCap) break;
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
      if (rows.size() < eventBatch) break;
    }
    return sb.toString();
  }

  @Transactional(readOnly = true)
  public String exportStatsCsv(Long userId, ShortCode shortCode, String dimension) {
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
      default -> throw new LinkException(LinkErrorCode.INVALID_EXPORT_DIMENSION, dim);
    }
    return sb.toString();
  }
}
