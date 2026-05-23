package com.example.short_link.link.application.read;

import com.example.short_link.link.application.LinkNotFoundException;
import com.example.short_link.link.application.LinkNotOwnedException;
import com.example.short_link.link.application.WebhookSummary;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.LinkRepository;
import com.example.short_link.link.domain.LinkWebhookRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LinkWebhookQueryService {

  private final LinkRepository linkRepository;
  private final LinkWebhookRepository repository;

  public List<WebhookSummary> list(Long userId, String shortCode) {
    LinkEntity link =
        linkRepository
            .findByShortCode(shortCode)
            .orElseThrow(() -> new LinkNotFoundException(shortCode));
    if (!link.isOwnedBy(userId)) throw new LinkNotOwnedException(shortCode);
    return repository.findAllByLinkIdOrderByIdAsc(link.getId()).stream()
        .map(WebhookSummary::from)
        .toList();
  }
}
