package com.example.short_link.link.webhook.application.write;

import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.exception.LinkErrorCode;
import com.example.short_link.link.exception.LinkException;
import com.example.short_link.link.webhook.domain.LinkWebhookEntity;
import com.example.short_link.link.webhook.domain.repository.LinkWebhookRepository;
import com.example.short_link.link.webhook.exception.WebhookErrorCode;
import com.example.short_link.link.webhook.exception.WebhookException;
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

  LinkEntity ownedLink(Long userId, ShortCode shortCode) {
    LinkEntity link =
        linkRepository
            .findByShortCode(shortCode)
            .orElseThrow(() -> new LinkException(LinkErrorCode.LINK_NOT_FOUND, shortCode));
    if (!link.isOwnedBy(userId)) throw new LinkException(LinkErrorCode.LINK_NOT_OWNED, shortCode);
    return link;
  }

  LinkWebhookEntity ownedHook(Long userId, ShortCode shortCode, Long webhookId) {
    LinkEntity link = ownedLink(userId, shortCode);
    LinkWebhookEntity hook =
        repository
            .findById(webhookId)
            .orElseThrow(() -> new WebhookException(WebhookErrorCode.WEBHOOK_NOT_FOUND));
    if (!hook.getLinkId().equals(link.getId()))
      throw new WebhookException(WebhookErrorCode.WEBHOOK_NOT_FOUND);
    return hook;
  }
}
