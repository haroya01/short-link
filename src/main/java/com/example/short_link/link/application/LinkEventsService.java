package com.example.short_link.link.application;

import com.example.short_link.link.api.LinkEventResponse;
import com.example.short_link.link.api.LinkEventsPage;
import com.example.short_link.link.domain.ClickEventEntity;
import com.example.short_link.link.domain.ClickEventRepository;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.LinkRepository;
import com.example.short_link.link.exception.InvalidCursorException;
import com.example.short_link.link.exception.LinkNotFoundException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LinkEventsService {

  private static final int DEFAULT_LIMIT = 50;
  private static final int MAX_LIMIT = 200;

  private final LinkRepository linkRepository;
  private final ClickEventRepository clickRepository;
  private final ReferrerChannelClassifier channelClassifier;
  private final LinkAccessGuard accessGuard;

  @Transactional(readOnly = true)
  public LinkEventsPage events(Long userId, String shortCode, String cursor, Integer limit) {
    LinkEntity link =
        linkRepository
            .findByShortCode(shortCode)
            .orElseThrow(() -> new LinkNotFoundException(shortCode));
    accessGuard.requireView(userId, link);
    int pageSize = clampLimit(limit);
    PageRequest req = PageRequest.ofSize(pageSize);

    List<ClickEventEntity> rows;
    if (cursor == null || cursor.isBlank()) {
      rows = clickRepository.findEventsByLinkIdLatest(link.getId(), req);
    } else {
      long cursorId = Cursor.decode(cursor);
      rows = clickRepository.findEventsByLinkIdBefore(link.getId(), cursorId, req);
    }

    List<LinkEventResponse> items = rows.stream().map(this::toResponse).toList();
    String next = rows.size() < pageSize ? null : Cursor.encode(rows.get(rows.size() - 1).getId());
    return new LinkEventsPage(items, next);
  }

  private LinkEventResponse toResponse(ClickEventEntity c) {
    String channel =
        c.getReferrer() == null ? "direct" : channelClassifier.classify(c.getReferrer());
    return new LinkEventResponse(
        c.getClickedAt(),
        c.getCountryCode(),
        c.getRegionName(),
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
        throw new InvalidCursorException();
      }
    }
  }
}
