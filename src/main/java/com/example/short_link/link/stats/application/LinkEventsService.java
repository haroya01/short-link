package com.example.short_link.link.stats.application;

import com.example.short_link.link.access.application.LinkAccessGuard;
import com.example.short_link.link.application.dto.AdminLinkClickView;
import com.example.short_link.link.application.dto.AdminLinkClicksResult;
import com.example.short_link.link.application.dto.LinkEventView;
import com.example.short_link.link.application.dto.LinkEventsResult;
import com.example.short_link.link.classifier.application.ReferrerChannelClassifier;
import com.example.short_link.link.classifier.application.helper.IpMasker;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.exception.LinkErrorCode;
import com.example.short_link.link.exception.LinkException;
import com.example.short_link.link.stats.domain.ClickEventEntity;
import com.example.short_link.link.stats.domain.repository.ClickRollupRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LinkEventsService {

  private static final int DEFAULT_LIMIT = 50;
  public static final int MAX_LIMIT = 200;

  private final LinkRepository linkRepository;
  private final LinkClickEventReader clickEvents;
  private final ReferrerChannelClassifier channelClassifier;
  private final LinkAccessGuard accessGuard;
  private final ClickRollupRepository rollups;

  @Transactional(readOnly = true)
  public LinkEventsResult events(Long userId, ShortCode shortCode, String cursor, Integer limit) {
    LinkEntity link = find(shortCode);
    accessGuard.requireView(userId, link);
    int pageSize = clampLimit(limit);
    List<ClickEventEntity> rows = page(link, cursor, pageSize);
    List<LinkEventView> items = rows.stream().map(this::toResponse).toList();
    String next = rows.size() < pageSize ? null : Cursor.encode(rows.get(rows.size() - 1).getId());
    return new LinkEventsResult(items, next);
  }

  @Transactional(readOnly = true)
  public AdminLinkClicksResult clicksForAdmin(ShortCode shortCode, String cursor, Integer limit) {
    LinkEntity link = find(shortCode);
    int pageSize = clampLimit(limit);
    List<ClickEventEntity> rows = page(link, cursor, pageSize);
    Instant rolledUntil = rollups.rolledUntil();
    List<AdminLinkClickView> items =
        rows.stream()
            .filter(click -> !click.getClickedAt().isBefore(rolledUntil))
            .map(
                click ->
                    new AdminLinkClickView(
                        click.getId(), ClickExclusionService.isExcluded(click), toResponse(click)))
            .toList();
    boolean ended = rows.size() < pageSize || items.size() < rows.size();
    String next = ended ? null : Cursor.encode(rows.get(rows.size() - 1).getId());
    return new AdminLinkClicksResult(items, next);
  }

  private LinkEntity find(ShortCode shortCode) {
    return linkRepository
        .findByShortCode(shortCode)
        .orElseThrow(() -> new LinkException(LinkErrorCode.LINK_NOT_FOUND, shortCode));
  }

  private List<ClickEventEntity> page(LinkEntity link, String cursor, int pageSize) {
    if (cursor == null || cursor.isBlank()) {
      return clickEvents.latest(link.linkId().value(), pageSize);
    }
    return clickEvents.before(link.linkId().value(), Cursor.decode(cursor), pageSize);
  }

  private LinkEventView toResponse(ClickEventEntity c) {
    String channel =
        c.getReferrer() == null ? "direct" : channelClassifier.classify(c.getReferrer());
    return new LinkEventView(
        c.getClickedAt(),
        c.getCountryCode(),
        c.getRegionName(),
        c.getRegionCode(),
        c.getCityName(),
        c.getDeviceClass(),
        c.getOsName(),
        c.getBrowserName(),
        c.getReferrer(),
        c.getReferrerHost(),
        channel,
        c.getLanguage(),
        c.isBot(),
        c.getBotName(),
        IpMasker.mask(c.getClientIp()));
  }

  static int clampLimit(Integer limit) {
    if (limit == null) return DEFAULT_LIMIT;
    if (limit <= 0) return DEFAULT_LIMIT;
    return Math.min(limit, MAX_LIMIT);
  }

  static final class Cursor {
    private Cursor() {}

    static String encode(Long id) {
      return Base64.getUrlEncoder()
          .withoutPadding()
          .encodeToString(id.toString().getBytes(StandardCharsets.UTF_8));
    }

    static long decode(String cursor) {
      try {
        String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
        return Long.parseLong(raw);
      } catch (IllegalArgumentException e) {
        throw new LinkException(LinkErrorCode.INVALID_CURSOR);
      }
    }
  }
}
