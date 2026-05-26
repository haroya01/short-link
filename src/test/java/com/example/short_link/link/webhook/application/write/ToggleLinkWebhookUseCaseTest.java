package com.example.short_link.link.webhook.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.short_link.link.domain.LinkId;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.webhook.application.dto.WebhookSummary;
import com.example.short_link.link.webhook.domain.LinkWebhookEntity;
import org.junit.jupiter.api.Test;

class ToggleLinkWebhookUseCaseTest {

  private final WebhookOwnership ownership = mock(WebhookOwnership.class);
  private final ToggleLinkWebhookUseCase useCase = new ToggleLinkWebhookUseCase(ownership);

  private LinkWebhookEntity hook() {
    LinkWebhookEntity h =
        new LinkWebhookEntity(new LinkId(1L), "https://example.com/h", "secret", "n");
    when(ownership.ownedHook(7L, new ShortCode("abcde"), 99L)).thenReturn(h);
    return h;
  }

  @Test
  void enableTrueEnablesHook() {
    LinkWebhookEntity h = hook();
    h.disable();

    WebhookSummary out =
        useCase.execute(new ToggleLinkWebhookCommand(7L, new ShortCode("abcde"), 99L, true));

    assertThat(out.enabled()).isTrue();
  }

  @Test
  void enableFalseDisablesHook() {
    hook();

    WebhookSummary out =
        useCase.execute(new ToggleLinkWebhookCommand(7L, new ShortCode("abcde"), 99L, false));

    assertThat(out.enabled()).isFalse();
  }

  @Test
  void enableClearsConsecutiveFailures() {
    LinkWebhookEntity h = hook();
    h.recordFailure(500, "boom");
    h.recordFailure(500, "boom");

    useCase.execute(new ToggleLinkWebhookCommand(7L, new ShortCode("abcde"), 99L, true));

    assertThat(h.getConsecutiveFailures()).isZero();
  }
}
