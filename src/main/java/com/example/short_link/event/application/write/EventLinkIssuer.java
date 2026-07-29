package com.example.short_link.event.application.write;

import com.example.short_link.event.application.helper.EventPublicUrlBuilder;
import com.example.short_link.event.domain.EventLinkEntity;
import com.example.short_link.event.domain.repository.EventLinkRepository;
import com.example.short_link.link.application.write.CreateLinkCommand;
import com.example.short_link.link.application.write.CreateLinkUseCase;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.domain.repository.LinkRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 이벤트의 배포용 단축링크 발급. 별칭마다 독립 단축 코드 (deduplicate=false — 같은 목적지라도 채널별로 코드가 달라야 클릭이 채널로 갈린다). 발급 실패(쿼터
 * 초과 등)는 이벤트 저장을 막지 않는 best-effort — {@code CtaLinkTracker} 와 같은 원칙.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventLinkIssuer {

  private final CreateLinkUseCase createLink;
  private final LinkRepository linkRepository;
  private final EventLinkRepository eventLinkRepository;
  private final EventPublicUrlBuilder urlBuilder;

  public record IssuedLink(Long linkId, String shortCode) {}

  public Optional<IssuedLink> issue(Long userId, Long eventId, String slug, String label) {
    try {
      ShortCode code =
          createLink
              .execute(
                  new CreateLinkCommand(urlBuilder.build(slug), userId, null, null, false, true))
              .shortCode();
      Optional<LinkEntity> link = linkRepository.findByShortCode(code);
      if (link.isEmpty()) {
        return Optional.empty();
      }
      Long linkId = link.get().getId();
      eventLinkRepository.save(new EventLinkEntity(eventId, linkId, label));
      return Optional.of(new IssuedLink(linkId, code.value()));
    } catch (RuntimeException e) {
      log.warn("event link issue skipped for event {}: {}", eventId, e.getMessage());
      return Optional.empty();
    }
  }
}
