package com.example.short_link.event.application;

import com.example.short_link.event.application.RegistrationClickLookup.ClickSnapshot;
import com.example.short_link.event.application.RegistrationClickLookup.LinkVisitor;
import com.example.short_link.event.domain.EventLinkEntity;
import com.example.short_link.event.domain.EventRegistrationEntity;
import com.example.short_link.event.domain.repository.EventLinkRepository;
import com.example.short_link.link.classifier.application.helper.VisitorHasher;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 신청을 유입 클릭에 귀속. 클릭 기록과 같은 {@code VisitorHasher} 를 신청 요청의 IP+UA 로 재계산해 매칭한다. best-effort — 매칭 실패(직접
 * 유입, GPC 옵트아웃, NAT 변경)면 채널 없이 저장.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RegistrationAttributor {

  private final EventLinkRepository eventLinkRepository;
  private final RegistrationClickLookup clickLookup;

  public void attribute(
      Long eventId, EventRegistrationEntity registration, String clientIp, String userAgent) {
    try {
      List<EventLinkEntity> links = eventLinkRepository.findAllByEventId(eventId);
      if (links.isEmpty()) return;
      List<LinkVisitor> candidates =
          links.stream()
              .map(
                  link ->
                      new LinkVisitor(
                          link.getLinkId(),
                          VisitorHasher.hash(link.getLinkId(), clientIp, userAgent)))
              .toList();
      Optional<ClickSnapshot> click = clickLookup.findLatestHumanClick(candidates);
      click.ifPresent(
          snapshot ->
              registration.attribute(
                  snapshot.linkId(),
                  snapshot.sourceChannel(),
                  snapshot.clientApp(),
                  snapshot.referrerHost(),
                  snapshot.utmSource(),
                  candidates.stream()
                      .filter(c -> c.linkId().equals(snapshot.linkId()))
                      .findFirst()
                      .map(LinkVisitor::visitorHash)
                      .orElse(null)));
    } catch (RuntimeException e) {
      log.warn("registration attribution skipped for event {}: {}", eventId, e.getMessage());
    }
  }
}
