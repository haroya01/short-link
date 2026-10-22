package com.example.short_link.link.export.application;

import com.example.short_link.common.csv.CsvCells;
import com.example.short_link.link.access.application.LinkAccessGuard;
import com.example.short_link.link.application.dto.LinkStats;
import com.example.short_link.link.classifier.application.ReferrerChannelClassifier;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.exception.LinkErrorCode;
import com.example.short_link.link.exception.LinkException;
import com.example.short_link.link.export.application.properties.LinkExportProperties;
import com.example.short_link.link.stats.application.LinkClickEventReader;
import com.example.short_link.link.stats.application.read.LinkStatsQueryService;
import com.example.short_link.link.stats.domain.ClickEventEntity;
import java.util.List;
import java.util.Locale;
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
  private final LinkExportProperties exportProperties;

  @Transactional(readOnly = true)
  public String exportEventsCsv(Long userId, ShortCode shortCode) {
    LinkEntity link =
        linkRepository
            .findByShortCode(shortCode)
            .orElseThrow(() -> new LinkException(LinkErrorCode.LINK_NOT_FOUND, shortCode));
    accessGuard.requireView(userId, link);
    StringBuilder sb = new StringBuilder(64 * 1024);
    CsvCells.appendRow(
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
    while (written < exportProperties.eventCap()) {
      List<ClickEventEntity> rows;
      if (cursorId == null) {
        rows = clickEvents.latest(link.linkId().value(), exportProperties.eventBatch());
      } else {
        rows = clickEvents.before(link.linkId().value(), cursorId, exportProperties.eventBatch());
      }
      if (rows.isEmpty()) break;
      for (ClickEventEntity c : rows) {
        if (written >= exportProperties.eventCap()) break;
        String channel =
            c.getReferrer() == null ? "direct" : channelClassifier.classify(c.getReferrer());
        CsvCells.appendRow(
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
      if (rows.size() < exportProperties.eventBatch()) break;
    }
    return sb.toString();
  }

  @Transactional(readOnly = true)
  public String exportStatsCsv(Long userId, ShortCode shortCode, String dimension) {
    LinkStats stats = statsService.stats(userId, shortCode);
    StringBuilder sb = new StringBuilder(8 * 1024);
    String dim = dimension == null ? "daily" : dimension.toLowerCase(Locale.ROOT);
    switch (dim) {
      case "daily" -> {
        CsvCells.appendRow(sb, "date", "count");
        stats.dailyClicks().forEach(d -> CsvCells.appendRow(sb, d.date(), d.count()));
      }
      case "hourly" -> {
        CsvCells.appendRow(sb, "hour", "count");
        stats.hourClicks().forEach(d -> CsvCells.appendRow(sb, d.hour(), d.count()));
      }
      case "country" -> {
        CsvCells.appendRow(sb, "country", "count");
        stats.countryClicks().forEach(d -> CsvCells.appendRow(sb, d.country(), d.count()));
      }
      case "city" -> {
        CsvCells.appendRow(sb, "city", "count");
        stats.cityClicks().forEach(d -> CsvCells.appendRow(sb, d.city(), d.count()));
      }
      case "device" -> {
        CsvCells.appendRow(sb, "device", "count");
        stats.deviceClicks().forEach(d -> CsvCells.appendRow(sb, d.device(), d.count()));
      }
      case "os" -> {
        CsvCells.appendRow(sb, "os", "count");
        stats.osClicks().forEach(d -> CsvCells.appendRow(sb, d.os(), d.count()));
      }
      case "browser" -> {
        CsvCells.appendRow(sb, "browser", "count");
        stats.browserClicks().forEach(d -> CsvCells.appendRow(sb, d.browser(), d.count()));
      }
      case "referrer" -> {
        CsvCells.appendRow(sb, "referrer", "count");
        stats.referrerClicks().forEach(d -> CsvCells.appendRow(sb, d.referrer(), d.count()));
      }
      case "channel" -> {
        CsvCells.appendRow(sb, "channel", "count");
        stats.channelClicks().forEach(d -> CsvCells.appendRow(sb, d.channel(), d.count()));
      }
      default -> throw new LinkException(LinkErrorCode.INVALID_EXPORT_DIMENSION, dim);
    }
    return sb.toString();
  }
}
