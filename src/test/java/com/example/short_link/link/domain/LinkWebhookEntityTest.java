package com.example.short_link.link.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.link.domain.repository.*;
import org.junit.jupiter.api.Test;

class LinkWebhookEntityTest {

  @Test
  void recordSuccessResetsConsecutiveFailures() {
    LinkWebhookEntity hook =
        new LinkWebhookEntity(1L, "https://example.com/hook", "secret", "name");
    hook.recordFailure(500, "boom");
    hook.recordFailure(500, "boom");
    assertThat(hook.getConsecutiveFailures()).isEqualTo(2);
    hook.recordSuccess(200);
    assertThat(hook.getConsecutiveFailures()).isZero();
    assertThat(hook.isEnabled()).isTrue();
  }

  @Test
  void fiveConsecutiveFailuresAutoDisableHook() {
    LinkWebhookEntity hook =
        new LinkWebhookEntity(1L, "https://example.com/hook", "secret", "name");
    for (int i = 0; i < LinkWebhookEntity.AUTO_DISABLE_FAILURE_THRESHOLD; i++) {
      hook.recordFailure(500, "boom");
    }
    assertThat(hook.isEnabled()).isFalse();
    assertThat(hook.getAutoDisabledReason()).contains("auto-disabled after 5");
  }

  @Test
  void manualEnableClearsAutoDisableReasonAndCounter() {
    LinkWebhookEntity hook =
        new LinkWebhookEntity(1L, "https://example.com/hook", "secret", "name");
    for (int i = 0; i < LinkWebhookEntity.AUTO_DISABLE_FAILURE_THRESHOLD; i++) {
      hook.recordFailure(500, "boom");
    }
    hook.enable();
    assertThat(hook.isEnabled()).isTrue();
    assertThat(hook.getAutoDisabledReason()).isNull();
    assertThat(hook.getConsecutiveFailures()).isZero();
  }

  @Test
  void updateConfigClampsSampleRateAndZeroQuota() {
    LinkWebhookEntity hook =
        new LinkWebhookEntity(1L, "https://example.com/hook", "secret", "name");
    hook.updateConfig(true, 200, true, 0, "twitter.com", "instagram");
    assertThat(hook.isIncludeBots()).isTrue();
    assertThat(hook.getSampleRate()).isEqualTo(100);
    assertThat(hook.isBatchEnabled()).isTrue();
    assertThat(hook.getDailyQuota()).isNull();
    assertThat(hook.getReferrerHostFilter()).isEqualTo("twitter.com");
    assertThat(hook.getUtmSourceFilter()).isEqualTo("instagram");

    hook.updateConfig(null, -5, null, null, null, null);
    assertThat(hook.getSampleRate()).isEqualTo(1);
  }
}
