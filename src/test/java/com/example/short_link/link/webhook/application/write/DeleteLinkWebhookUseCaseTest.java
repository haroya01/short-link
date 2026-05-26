package com.example.short_link.link.webhook.application.write;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.link.domain.LinkId;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.webhook.domain.LinkWebhookEntity;
import com.example.short_link.link.webhook.domain.repository.LinkWebhookRepository;
import org.junit.jupiter.api.Test;

class DeleteLinkWebhookUseCaseTest {

  private final WebhookOwnership ownership = mock(WebhookOwnership.class);
  private final LinkWebhookRepository repository = mock(LinkWebhookRepository.class);
  private final DeleteLinkWebhookUseCase useCase =
      new DeleteLinkWebhookUseCase(ownership, repository);

  @Test
  void executeDeletesTheResolvedHook() {
    LinkWebhookEntity hook =
        new LinkWebhookEntity(new LinkId(1L), "https://example.com/h", "secret", "n");
    when(ownership.ownedHook(7L, new ShortCode("abcde"), 99L)).thenReturn(hook);

    useCase.execute(new DeleteLinkWebhookCommand(7L, new ShortCode("abcde"), 99L));

    verify(repository).delete(hook);
  }
}
