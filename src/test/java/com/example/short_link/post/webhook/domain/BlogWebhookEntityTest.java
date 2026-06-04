package com.example.short_link.post.webhook.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.common.event.BlogInteractionType;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BlogWebhookEntityTest {

  private static BlogWebhookEntity hook(Set<BlogInteractionType> events) {
    return new BlogWebhookEntity(
        7L, "https://example.com/hook", "secret", "name", BlogWebhookFormat.GENERIC, events);
  }

  @Test
  void emptyEventSetDefaultsToAllInteractions() {
    BlogWebhookEntity h = hook(Set.of());
    assertThat(h.events()).containsExactlyInAnyOrder(BlogInteractionType.values());
  }

  @Test
  void firesOnlyOnSubscribedInteractionsWhileEnabled() {
    BlogWebhookEntity h = hook(EnumSet.of(BlogInteractionType.LIKE, BlogInteractionType.FOLLOW));
    assertThat(h.firesOn(BlogInteractionType.LIKE)).isTrue();
    assertThat(h.firesOn(BlogInteractionType.FOLLOW)).isTrue();
    assertThat(h.firesOn(BlogInteractionType.COMMENT)).isFalse();
  }

  @Test
  void disabledHookFiresOnNothing() {
    BlogWebhookEntity h = hook(EnumSet.of(BlogInteractionType.LIKE));
    h.update(null, null, false);
    assertThat(h.firesOn(BlogInteractionType.LIKE)).isFalse();
    assertThat(h.isEnabled()).isFalse();
  }

  @Test
  void genericIsSignedOthersAreNot() {
    assertThat(hook(Set.of()).signed()).isTrue();
    BlogWebhookEntity discord =
        new BlogWebhookEntity(
            7L, "https://discord.com/x", "s", null, BlogWebhookFormat.DISCORD, Set.of());
    assertThat(discord.signed()).isFalse();
  }

  @Test
  void updateReplacesEventsRenamesAndReenables() {
    BlogWebhookEntity h = hook(EnumSet.of(BlogInteractionType.LIKE));
    h.update("  new name  ", EnumSet.of(BlogInteractionType.COMMENT), true);
    assertThat(h.getName()).isEqualTo("new name");
    assertThat(h.events()).containsExactly(BlogInteractionType.COMMENT);
    assertThat(h.isEnabled()).isTrue();
  }

  @Test
  void blankNameUpdateClearsIt() {
    BlogWebhookEntity h = hook(Set.of());
    h.update("   ", null, null);
    assertThat(h.getName()).isNull();
  }

  @Test
  void recordSuccessClearsFailureState() {
    BlogWebhookEntity h = hook(Set.of());
    h.recordFailure(500, "boom");
    h.recordSuccess(200);
    assertThat(h.getConsecutiveFailures()).isZero();
    assertThat(h.getLastStatusCode()).isEqualTo(200);
    assertThat(h.getLastError()).isNull();
  }

  @Test
  void autoDisablesAfterFiveConsecutiveFailures() {
    BlogWebhookEntity h = hook(Set.of());
    for (int i = 0; i < 5; i++) {
      h.recordFailure(503, "still failing");
    }
    assertThat(h.isEnabled()).isFalse();
    assertThat(h.getConsecutiveFailures()).isEqualTo(5);
    assertThat(h.getAutoDisabledReason()).contains("auto-disabled after 5");
  }

  @Test
  void enableResetsFailureCounter() {
    BlogWebhookEntity h = hook(Set.of());
    h.recordFailure(500, "x");
    h.enable();
    assertThat(h.getConsecutiveFailures()).isZero();
    assertThat(h.getAutoDisabledReason()).isNull();
    assertThat(h.isEnabled()).isTrue();
  }

  @Test
  void unknownEventTokensAreIgnored() {
    BlogWebhookEntity h = hook(EnumSet.of(BlogInteractionType.LIKE));
    org.springframework.test.util.ReflectionTestUtils.setField(h, "events", "LIKE, ,BOGUS,COMMENT");
    assertThat(h.events())
        .containsExactlyInAnyOrder(BlogInteractionType.LIKE, BlogInteractionType.COMMENT);
  }
}
