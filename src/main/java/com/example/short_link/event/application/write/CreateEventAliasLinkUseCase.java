package com.example.short_link.event.application.write;

import com.example.short_link.event.application.write.EventLinkIssuer.IssuedLink;
import com.example.short_link.event.domain.EventEntity;
import com.example.short_link.event.domain.repository.EventRepository;
import com.example.short_link.event.exception.EventErrorCode;
import com.example.short_link.event.exception.EventException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 채널별 별칭 링크 ("단톡용", "트위터용" …) — UTM 을 모르는 주최자의 채널 분석 UX. */
@Service
@RequiredArgsConstructor
public class CreateEventAliasLinkUseCase {

  private final EventRepository eventRepository;
  private final EventLinkIssuer linkIssuer;

  @Transactional
  public IssuedLink execute(Long userId, Long eventId, String label) {
    EventEntity event =
        eventRepository
            .findById(eventId)
            .orElseThrow(() -> new EventException(EventErrorCode.EVENT_NOT_FOUND, eventId));
    if (!event.isOwnedBy(userId)) {
      throw new EventException(EventErrorCode.EVENT_PERMISSION_DENIED);
    }
    String cleaned = label == null ? "" : label.trim();
    if (cleaned.isEmpty() || cleaned.length() > 50) {
      throw new EventException(EventErrorCode.INVALID_QUESTIONS, "label");
    }
    return linkIssuer
        .issue(userId, eventId, event.getSlug(), cleaned)
        .orElseThrow(() -> new EventException(EventErrorCode.SLUG_EXHAUSTED));
  }
}
