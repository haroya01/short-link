package com.example.short_link.post.webhook.scheduler;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.example.short_link.common.crypto.SecretCipher;
import com.example.short_link.common.event.BlogInteractionEvent;
import com.example.short_link.common.net.HttpFetcher;
import com.example.short_link.post.webhook.domain.repository.BlogWebhookActorReader;
import com.example.short_link.post.webhook.domain.repository.BlogWebhookRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

class BlogWebhookDispatcherRoomTest {

  private final BlogWebhookRepository hooks = mock(BlogWebhookRepository.class);
  private final HttpFetcher http = mock(HttpFetcher.class);
  private final BlogWebhookDispatcher dispatcher =
      new BlogWebhookDispatcher(
          hooks,
          mock(BlogWebhookActorReader.class),
          JsonMapper.builder().build(),
          http,
          new SimpleMeterRegistry(),
          mock(SecretCipher.class),
          mock(TransactionTemplate.class));

  @Test
  void whatHappensOnARoomPostNeverLeavesForAWebhook() {
    dispatcher.onBlogInteraction(
        BlogInteractionEvent.like(1L, 2L, 3L, "s", "a room title", Instant.now()).onRoomPost(true));

    verifyNoInteractions(hooks, http);
  }

  @Test
  void anOpenPostStillLooksForTheAuthorsWebhooks() {
    dispatcher.onBlogInteraction(
        BlogInteractionEvent.like(1L, 2L, 3L, "s", "t", Instant.now()).onRoomPost(false));

    verify(hooks).findAllByUserIdAndEnabledTrue(1L);
  }
}
