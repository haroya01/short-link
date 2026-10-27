package com.example.short_link.link.webhook.application.write;

import com.example.short_link.link.application.write.LinkOwnership;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.webhook.domain.LinkWebhookEntity;
import com.example.short_link.link.webhook.domain.repository.LinkWebhookRepository;
import com.example.short_link.link.webhook.exception.WebhookErrorCode;
import com.example.short_link.link.webhook.exception.WebhookException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class WebhookOwnership {

  private final LinkOwnership ownership;
  private final LinkWebhookRepository repository;

  LinkEntity ownedLink(Long userId, ShortCode shortCode) {
    LinkEntity link = ownership.requireOwned(userId, shortCode);
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
