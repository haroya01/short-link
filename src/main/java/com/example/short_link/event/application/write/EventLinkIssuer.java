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

/** 같은 목적지라도 채널별 클릭을 구분하려고 deduplicate=false로 발급한다. 쿼터 초과 등 발급 실패는 이벤트 저장을 막지 않는다. */
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
