package com.example.short_link.link.webhook.application.write;

import com.example.short_link.link.webhook.application.dto.WebhookSummary;
import com.example.short_link.link.webhook.domain.LinkWebhookEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ToggleLinkWebhookUseCase {

  private final WebhookOwnership ownership;

  @Transactional
  public WebhookSummary execute(ToggleLinkWebhookCommand cmd) {
    LinkWebhookEntity hook = ownership.ownedHook(cmd.userId(), cmd.shortCode(), cmd.webhookId());
    if (cmd.enabled()) hook.enable();
    else hook.disable();
    return WebhookSummary.from(hook);
  }
}
