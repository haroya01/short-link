package com.example.short_link.link.application.write;

import com.example.short_link.link.application.WebhookSummary;
import com.example.short_link.link.domain.LinkWebhookEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UpdateLinkWebhookConfigUseCase {

  private final WebhookOwnership ownership;

  @Transactional
  public WebhookSummary execute(UpdateLinkWebhookConfigCommand cmd) {
    LinkWebhookEntity hook = ownership.ownedHook(cmd.userId(), cmd.shortCode(), cmd.webhookId());
    hook.updateConfig(
        cmd.includeBots(),
        cmd.sampleRate(),
        cmd.batchEnabled(),
        cmd.dailyQuota(),
        cmd.referrerHostFilter(),
        cmd.utmSourceFilter());
    return WebhookSummary.from(hook);
  }
}
