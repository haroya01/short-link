package com.example.short_link.link.application.write;

import com.example.short_link.link.application.LinkNotFoundException;
import com.example.short_link.link.application.LinkNotOwnedException;
import com.example.short_link.link.application.WebhookNotFoundException;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.LinkRepository;
import com.example.short_link.link.domain.LinkWebhookEntity;
import com.example.short_link.link.domain.LinkWebhookRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Ownership guard for link-scoped webhook operations. Every write use case checks both the link and
 * the hook belong to the caller before mutating — owner-scoped contract from the original
 * LinkWebhookService.
 */
@Component
@RequiredArgsConstructor
class WebhookOwnership {

  private final LinkRepository linkRepository;
  private final LinkWebhookRepository repository;

  LinkEntity ownedLink(Long userId, String shortCode) {
    LinkEntity link =
        linkRepository
            .findByShortCode(shortCode)
            .orElseThrow(() -> new LinkNotFoundException(shortCode));
    if (!link.isOwnedBy(userId)) throw new LinkNotOwnedException(shortCode);
    return link;
  }

  LinkWebhookEntity ownedHook(Long userId, String shortCode, Long webhookId) {
    LinkEntity link = ownedLink(userId, shortCode);
    LinkWebhookEntity hook =
        repository.findById(webhookId).orElseThrow(WebhookNotFoundException::new);
    if (!hook.getLinkId().equals(link.getId())) throw new WebhookNotFoundException();
    return hook;
  }
}
