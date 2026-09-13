package com.example.short_link.event.application.read;

import com.example.short_link.common.eventlink.EventLinkPreview;
import com.example.short_link.common.eventlink.EventLinkPreviewPort;
import com.example.short_link.common.storage.ObjectStoragePublicUrls;
import com.example.short_link.event.domain.EventEntity;
import com.example.short_link.event.domain.repository.EventLinkRepository;
import com.example.short_link.event.domain.repository.EventRepository;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 미리보기 일시는 주최자가 정한 이벤트 타임존으로 표시한다. */
@Component
@RequiredArgsConstructor
public class EventLinkPreviewAdapter implements EventLinkPreviewPort {

  // 크롤러 미리보기는 보는 사람 로케일을 알 수 없다 — 주 시장 표기 하나로 고정.
  private static final DateTimeFormatter WHEN =
      DateTimeFormatter.ofPattern("M월 d일 (E) HH:mm", Locale.KOREAN);

  private final EventLinkRepository eventLinks;
  private final EventRepository events;
  private final ObjectStoragePublicUrls coverImageUrls;

  @Override
  @Transactional(readOnly = true)
  public Optional<EventLinkPreview> findByLinkId(long linkId) {
    return eventLinks
        .findByLinkId(linkId)
        .flatMap(link -> events.findById(link.getEventId()))
        .map(this::toPreview);
  }

  private EventLinkPreview toPreview(EventEntity event) {
    String when = WHEN.format(event.getStartsAt().atZone(zoneOf(event.getTimezone())));
    String location = event.getLocationText();
    String description = location == null || location.isBlank() ? when : when + " · " + location;
    return new EventLinkPreview(
        event.getTitle(), description, coverImageUrls.forKey(event.getCoverImageKey()));
  }

  private static ZoneId zoneOf(String timezone) {
    try {
      return ZoneId.of(timezone);
    } catch (DateTimeException e) {
      return ZoneId.of("Asia/Seoul");
    }
  }
}
