package com.example.short_link.link.webhook.application.read;

import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.exception.LinkErrorCode;
import com.example.short_link.link.exception.LinkException;
import com.example.short_link.link.webhook.application.dto.WebhookSummary;
import com.example.short_link.link.webhook.domain.repository.LinkWebhookRepository;
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
            .orElseThrow(() -> new LinkException(LinkErrorCode.LINK_NOT_FOUND, shortCode));
    if (!link.isOwnedBy(userId)) throw new LinkException(LinkErrorCode.LINK_NOT_OWNED, shortCode);
    return repository.findAllByLinkIdOrderByIdAsc(link.getId()).stream()
        .map(WebhookSummary::from)
        .toList();
  }
}
