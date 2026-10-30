package com.example.short_link.event.application.write;

import com.example.short_link.event.application.helper.EventPublicUrlBuilder;
import com.example.short_link.event.domain.EventLinkEntity;
import com.example.short_link.event.domain.repository.EventLinkRepository;
import com.example.short_link.link.application.dto.LinkRef;
import com.example.short_link.link.application.read.LinkRefReader;
import com.example.short_link.link.application.write.CreateLinkUseCase;
import com.example.short_link.link.domain.ShortCode;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// 같은 목적지라도 채널별 클릭을 구분하려고 deduplicate=false로 발급한다. 쿼터 초과 등 발급 실패는 이벤트 저장을 막지 않는다.
@Component
@RequiredArgsConstructor
public class EventLinkIssuer {

  private final CreateLinkUseCase createLink;
  private final LinkRefReader links;
  private final EventLinkRepository eventLinkRepository;
  private final EventPublicUrlBuilder urlBuilder;

  public record IssuedLink(Long linkId, String shortCode) {}

  public Optional<IssuedLink> issue(Long userId, Long eventId, String slug, String label) {
    return createLink
        .tryCreateForOwnPage(urlBuilder.build(slug), userId)
        .flatMap(created -> record(eventId, created.shortCode(), label));
  }

  private Optional<IssuedLink> record(Long eventId, ShortCode code, String label) {
    Optional<LinkRef> link = links.find(code);
    if (link.isEmpty()) {
      return Optional.empty();
    }
    Long linkId = link.get().linkId().value();
    eventLinkRepository.save(new EventLinkEntity(eventId, linkId, label));
    return Optional.of(new IssuedLink(linkId, code.value()));
  }
}
