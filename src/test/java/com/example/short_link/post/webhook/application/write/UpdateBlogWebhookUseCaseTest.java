package com.example.short_link.post.webhook.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.example.short_link.common.event.BlogInteractionType;
import com.example.short_link.post.exception.PostErrorCode;
import com.example.short_link.post.exception.PostException;
import com.example.short_link.post.webhook.application.dto.BlogWebhookSummary;
import com.example.short_link.post.webhook.domain.BlogWebhookEntity;
import com.example.short_link.post.webhook.domain.BlogWebhookFormat;
import com.example.short_link.post.webhook.domain.repository.BlogWebhookRepository;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UpdateBlogWebhookUseCaseTest {

  @Mock private BlogWebhookRepository repository;

  private BlogWebhookEntity hook() {
    return new BlogWebhookEntity(
        7L,
        "https://example.com/h",
        "s",
        "old",
        BlogWebhookFormat.GENERIC,
        EnumSet.of(BlogInteractionType.LIKE));
  }

  @Test
  void updatesEventsAndEnabledForOwnedHook() {
    BlogWebhookEntity hook = hook();
    when(repository.findByIdAndUserId(3L, 7L)).thenReturn(Optional.of(hook));
    when(repository.save(hook)).thenReturn(hook);

    BlogWebhookSummary summary =
        new UpdateBlogWebhookUseCase(repository)
            .execute(7L, 3L, "renamed", EnumSet.of(BlogInteractionType.COMMENT), false);

    assertThat(summary.name()).isEqualTo("renamed");
    assertThat(summary.events()).containsExactly(BlogInteractionType.COMMENT);
    assertThat(summary.enabled()).isFalse();
  }

  @Test
  void rejectsUnknownHook() {
    when(repository.findByIdAndUserId(3L, 7L)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> new UpdateBlogWebhookUseCase(repository).execute(7L, 3L, null, Set.of(), null))
        .isInstanceOf(PostException.class)
        .extracting(e -> ((PostException) e).errorCode())
        .isEqualTo(PostErrorCode.WEBHOOK_NOT_FOUND);
  }
}
