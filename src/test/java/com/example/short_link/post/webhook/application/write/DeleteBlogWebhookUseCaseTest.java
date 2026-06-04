package com.example.short_link.post.webhook.application.write;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.common.event.BlogInteractionType;
import com.example.short_link.post.exception.PostErrorCode;
import com.example.short_link.post.exception.PostException;
import com.example.short_link.post.webhook.domain.BlogWebhookEntity;
import com.example.short_link.post.webhook.domain.BlogWebhookFormat;
import com.example.short_link.post.webhook.domain.repository.BlogWebhookRepository;
import java.util.EnumSet;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeleteBlogWebhookUseCaseTest {

  @Mock private BlogWebhookRepository repository;

  @Test
  void deletesOwnedHook() {
    BlogWebhookEntity hook =
        new BlogWebhookEntity(
            7L,
            "https://example.com/h",
            "s",
            null,
            BlogWebhookFormat.GENERIC,
            EnumSet.of(BlogInteractionType.LIKE));
    when(repository.findByIdAndUserId(3L, 7L)).thenReturn(Optional.of(hook));

    new DeleteBlogWebhookUseCase(repository).execute(7L, 3L);

    verify(repository).delete(hook);
  }

  @Test
  void rejectsUnknownHook() {
    when(repository.findByIdAndUserId(3L, 7L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> new DeleteBlogWebhookUseCase(repository).execute(7L, 3L))
        .isInstanceOf(PostException.class)
        .extracting(e -> ((PostException) e).errorCode())
        .isEqualTo(PostErrorCode.WEBHOOK_NOT_FOUND);
  }
}
