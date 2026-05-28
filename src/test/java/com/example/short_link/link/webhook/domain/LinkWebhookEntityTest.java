package com.example.short_link.link.webhook.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.link.domain.LinkId;
import com.example.short_link.link.domain.repository.*;
import org.junit.jupiter.api.Test;

class LinkWebhookEntityTest {

  @Test
  void recordSuccessResetsConsecutiveFailures() {
    LinkWebhookEntity hook =
        new LinkWebhookEntity(new LinkId(1L), "https://example.com/hook", "secret", "name");
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
        new LinkWebhookEntity(new LinkId(1L), "https://example.com/hook", "secret", "name");
    for (int i = 0; i < LinkWebhookEntity.AUTO_DISABLE_FAILURE_THRESHOLD; i++) {
      hook.recordFailure(500, "boom");
    }
    assertThat(hook.isEnabled()).isFalse();
    assertThat(hook.getAutoDisabledReason()).contains("auto-disabled after 5");
  }

  @Test
  void manualEnableClearsAutoDisableReasonAndCounter() {
    LinkWebhookEntity hook =
        new LinkWebhookEntity(new LinkId(1L), "https://example.com/hook", "secret", "name");
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
        new LinkWebhookEntity(new LinkId(1L), "https://example.com/hook", "secret", "name");
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

  @Test
  void blankFilterStringsAreStoredAsNull() {
    LinkWebhookEntity hook =
        new LinkWebhookEntity(new LinkId(1L), "https://example.com/hook", "secret", "name");
    hook.updateConfig(null, null, null, null, "   ", "  ");
    assertThat(hook.getReferrerHostFilter()).isNull();
    assertThat(hook.getUtmSourceFilter()).isNull();
  }

  @Test
  void disableSetsEnabledFalseWithoutClearingFailureState() {
    LinkWebhookEntity hook =
        new LinkWebhookEntity(new LinkId(1L), "https://example.com/hook", "secret", "name");
    hook.recordFailure(500, "err");
    hook.disable();
    assertThat(hook.isEnabled()).isFalse();
    assertThat(hook.getConsecutiveFailures()).isEqualTo(1);
    assertThat(hook.getLastError()).isEqualTo("err");
  }

  @Test
  void resetFailureStateClearsErrorAndCounter() {
    LinkWebhookEntity hook =
        new LinkWebhookEntity(new LinkId(1L), "https://example.com/hook", "secret", "name");
    for (int i = 0; i < LinkWebhookEntity.AUTO_DISABLE_FAILURE_THRESHOLD; i++) {
      hook.recordFailure(500, "err");
    }
    assertThat(hook.isEnabled()).isFalse();

    hook.resetFailureState();
    assertThat(hook.isEnabled()).isTrue();
    assertThat(hook.getConsecutiveFailures()).isZero();
    assertThat(hook.getLastError()).isNull();
    assertThat(hook.getAutoDisabledReason()).isNull();
  }

  @Test
  void recordFailureWithNullErrorMessage() {
    LinkWebhookEntity hook =
        new LinkWebhookEntity(new LinkId(1L), "https://example.com/hook", "secret", "name");
    hook.recordFailure(500, null);
    assertThat(hook.getLastError()).isNull();
    assertThat(hook.getConsecutiveFailures()).isEqualTo(1);
  }

  @Test
  void recordFailureTruncatesLongMessage() {
    LinkWebhookEntity hook =
        new LinkWebhookEntity(new LinkId(1L), "https://example.com/hook", "secret", "name");
    hook.recordFailure(500, "x".repeat(800));
    assertThat(hook.getLastError()).hasSize(500);
  }

  @Test
  void autoDisableReasonHandlesNullLastError() {
    LinkWebhookEntity hook =
        new LinkWebhookEntity(new LinkId(1L), "https://example.com/hook", "secret", "name");
    for (int i = 0; i < LinkWebhookEntity.AUTO_DISABLE_FAILURE_THRESHOLD; i++) {
      hook.recordFailure(500, null);
    }
    assertThat(hook.isEnabled()).isFalse();
    assertThat(hook.getAutoDisabledReason()).contains("(no detail)");
  }

  @Test
  void changeFormatNullDefaultsToGeneric() {
    LinkWebhookEntity hook =
        new LinkWebhookEntity(
            new LinkId(1L), "https://example.com/hook", "secret", "name", WebhookFormat.SLACK);
    hook.changeFormat(null);
    assertThat(hook.getFormat()).isEqualTo(WebhookFormat.GENERIC);
  }

  @Test
  void constructorWithNullFormatDefaultsToGeneric() {
    LinkWebhookEntity hook =
        new LinkWebhookEntity(new LinkId(1L), "https://example.com/hook", "secret", "name", null);
    assertThat(hook.getFormat()).isEqualTo(WebhookFormat.GENERIC);
  }

  @Test
  void constructorWithNullLinkIdAllowsNullId() {
    LinkWebhookEntity hook =
        new LinkWebhookEntity(null, "https://example.com/hook", "secret", "name");
    assertThat(hook.getLinkId()).isNull();
  }
}
