package com.example.short_link.link.webhook.application.write;

import com.example.short_link.link.webhook.domain.LinkWebhookEntity;
import com.example.short_link.link.webhook.domain.repository.LinkWebhookRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeleteLinkWebhookUseCase {

  private final WebhookOwnership ownership;
  private final LinkWebhookRepository repository;

  @Transactional
  public void execute(DeleteLinkWebhookCommand cmd) {
    LinkWebhookEntity hook = ownership.ownedHook(cmd.userId(), cmd.shortCode(), cmd.webhookId());
    repository.delete(hook);
  }
}
