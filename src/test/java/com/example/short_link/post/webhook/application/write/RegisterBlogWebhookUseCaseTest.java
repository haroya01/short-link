package com.example.short_link.post.webhook.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.example.short_link.common.event.BlogInteractionType;
import com.example.short_link.common.net.PublicHttpUrlGuard;
import com.example.short_link.post.exception.PostErrorCode;
import com.example.short_link.post.exception.PostException;
import com.example.short_link.post.webhook.application.dto.IssuedBlogWebhook;
import com.example.short_link.post.webhook.domain.BlogWebhookEntity;
import com.example.short_link.post.webhook.domain.BlogWebhookFormat;
import com.example.short_link.post.webhook.domain.repository.BlogWebhookRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RegisterBlogWebhookUseCaseTest {

  @Mock private BlogWebhookRepository repository;
  private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

  private RegisterBlogWebhookUseCase useCase() {
    return new RegisterBlogWebhookUseCase(repository, registry);
  }

  @Test
  void registersWithIssuedSecretAndDetectedFormat() {
    when(repository.countByUserId(7L)).thenReturn(0L);
    when(repository.save(org.mockito.ArgumentMatchers.any(BlogWebhookEntity.class)))
        .thenAnswer(inv -> inv.getArgument(0));
    try (MockedStatic<PublicHttpUrlGuard> guard = mockStatic(PublicHttpUrlGuard.class)) {
      guard
          .when(() -> PublicHttpUrlGuard.isPublic("https://discord.com/api/webhooks/1/x"))
          .thenReturn(true);

      IssuedBlogWebhook issued =
          useCase().execute(7L, "https://discord.com/api/webhooks/1/x", "  My hook ", Set.of());

      assertThat(issued.secret()).hasSize(48);
      assertThat(issued.format()).isEqualTo(BlogWebhookFormat.DISCORD);
      assertThat(issued.name()).isEqualTo("My hook");
      // Empty event set ⇒ subscribe to every interaction.
      assertThat(issued.events()).containsExactlyInAnyOrder(BlogInteractionType.values());
    }
  }

  @Test
  void keepsExplicitEventSubsetAndTruncatesLongName() {
    when(repository.countByUserId(7L)).thenReturn(0L);
    when(repository.save(org.mockito.ArgumentMatchers.any(BlogWebhookEntity.class)))
        .thenAnswer(inv -> inv.getArgument(0));
    String longName = "x".repeat(150);
    try (MockedStatic<PublicHttpUrlGuard> guard = mockStatic(PublicHttpUrlGuard.class)) {
      guard.when(() -> PublicHttpUrlGuard.isPublic("https://example.com/h")).thenReturn(true);

      IssuedBlogWebhook issued =
          useCase()
              .execute(7L, "https://example.com/h", longName, Set.of(BlogInteractionType.FOLLOW));

      assertThat(issued.format()).isEqualTo(BlogWebhookFormat.GENERIC);
      assertThat(issued.events()).containsExactly(BlogInteractionType.FOLLOW);
      assertThat(issued.name()).hasSize(100);
    }
  }

  @Test
  void nullNameAndNullEventsRegisterWithDefaults() {
    when(repository.countByUserId(7L)).thenReturn(0L);
    when(repository.save(org.mockito.ArgumentMatchers.any(BlogWebhookEntity.class)))
        .thenAnswer(inv -> inv.getArgument(0));
    try (MockedStatic<PublicHttpUrlGuard> guard = mockStatic(PublicHttpUrlGuard.class)) {
      guard.when(() -> PublicHttpUrlGuard.isPublic("https://example.com/h")).thenReturn(true);

      IssuedBlogWebhook issued = useCase().execute(7L, "https://example.com/h", null, null);

      assertThat(issued.name()).isNull();
      assertThat(issued.events()).containsExactlyInAnyOrder(BlogInteractionType.values());
    }
  }

  @Test
  void blankNameBecomesNull() {
    when(repository.countByUserId(7L)).thenReturn(0L);
    when(repository.save(org.mockito.ArgumentMatchers.any(BlogWebhookEntity.class)))
        .thenAnswer(inv -> inv.getArgument(0));
    try (MockedStatic<PublicHttpUrlGuard> guard = mockStatic(PublicHttpUrlGuard.class)) {
      guard.when(() -> PublicHttpUrlGuard.isPublic("https://example.com/h")).thenReturn(true);

      IssuedBlogWebhook issued = useCase().execute(7L, "https://example.com/h", "   ", Set.of());

      assertThat(issued.name()).isNull();
    }
  }

  @Test
  void rejectsNonPublicUrl() {
    try (MockedStatic<PublicHttpUrlGuard> guard = mockStatic(PublicHttpUrlGuard.class)) {
      guard.when(() -> PublicHttpUrlGuard.isPublic("http://localhost/x")).thenReturn(false);

      assertThatThrownBy(() -> useCase().execute(7L, "http://localhost/x", null, Set.of()))
          .isInstanceOf(PostException.class)
          .extracting(e -> ((PostException) e).errorCode())
          .isEqualTo(PostErrorCode.INVALID_WEBHOOK_URL);
    }
  }

  @Test
  void rejectsWhenAtLimit() {
    when(repository.countByUserId(7L)).thenReturn((long) RegisterBlogWebhookUseCase.MAX_PER_USER);
    try (MockedStatic<PublicHttpUrlGuard> guard = mockStatic(PublicHttpUrlGuard.class)) {
      guard.when(() -> PublicHttpUrlGuard.isPublic("https://example.com/h")).thenReturn(true);

      assertThatThrownBy(() -> useCase().execute(7L, "https://example.com/h", null, Set.of()))
          .isInstanceOf(PostException.class)
          .extracting(e -> ((PostException) e).errorCode())
          .isEqualTo(PostErrorCode.TOO_MANY_WEBHOOKS);
    }
  }
}
