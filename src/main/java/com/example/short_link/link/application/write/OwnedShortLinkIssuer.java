package com.example.short_link.link.application.write;

import com.example.short_link.common.link.ShortLinkIssuer;
import com.example.short_link.link.application.ShortLinkUrlBuilder;
import com.example.short_link.link.application.dto.LinkCreated;
import com.example.short_link.link.domain.repository.LinkRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OwnedShortLinkIssuer implements ShortLinkIssuer {

  private final CreateLinkUseCase createLink;
  private final LinkRepository links;
  private final ShortLinkUrlBuilder urls;

  @Override
  public Optional<Issued> issue(Long ownerId, String url) {
    LinkCreated created;
    try {
      created = createLink.execute(CreateLinkCommand.of(url, ownerId, null, null));
    } catch (RuntimeException e) {
      log.warn("short link not issued for user {}: {}", ownerId, e.getMessage());
      return Optional.empty();
    }
    return links
        .findByShortCode(created.shortCode())
        .map(
            link ->
                new Issued(
                    link.getId(), link.getShortCode().value(), urls.build(link.getShortCode())));
  }
}
