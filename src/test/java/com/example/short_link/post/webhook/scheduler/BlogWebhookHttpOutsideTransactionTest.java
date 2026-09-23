package com.example.short_link.post.webhook.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.example.short_link.common.event.BlogInteractionEvent;
import com.example.short_link.common.event.BlogInteractionType;
import com.example.short_link.common.net.HttpFetcher;
import com.example.short_link.post.webhook.domain.BlogWebhookEntity;
import com.example.short_link.post.webhook.domain.BlogWebhookFormat;
import com.example.short_link.post.webhook.domain.repository.BlogWebhookRepository;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@SpringBootTest
@ActiveProfiles("test")
class BlogWebhookHttpOutsideTransactionTest {
  private static final String URL = "https://1.1.1.1/hook";

  @Autowired private UserRepository users;
  @Autowired private BlogWebhookRepository hooks;
  @Autowired private BlogWebhookDispatcher dispatcher;
  @MockitoBean private HttpFetcher httpFetcher;

  private final List<Boolean> transactionOpenDuringHttp = new CopyOnWriteArrayList<>();
  private UserEntity author;
  private UserEntity follower;
  private BlogWebhookEntity hook;

  @BeforeEach
  void anAuthorWithAFollowWebhook() {
    when(httpFetcher.fetch(any()))
        .thenAnswer(
            call -> {
              transactionOpenDuringHttp.add(
                  TransactionSynchronizationManager.isActualTransactionActive());
              return new HttpFetcher.Response(200, Map.of(), new byte[0]);
            });
    author = user();
    follower = user();
    hook =
        hooks.save(
            new BlogWebhookEntity(
                author.getId(),
                URL,
                "secret",
                "n",
                BlogWebhookFormat.DISCORD,
                Set.of(BlogInteractionType.FOLLOW)));
  }

  @AfterEach
  void removeHook() {
    hooks.delete(hooks.findById(hook.getId()).orElseThrow());
  }

  @Test
  void aFollowWebhookIsSentWithoutAnOpenTransactionAndItsOutcomeIsStored() {
    dispatcher.onBlogInteraction(
        BlogInteractionEvent.follow(author.getId(), follower.getId(), Instant.now()));

    assertThat(storedStatusWithin(Duration.ofSeconds(10))).isEqualTo(200);
    assertThat(transactionOpenDuringHttp).containsExactly(false);
  }

  private UserEntity user() {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    return users.save(new UserEntity(suffix + "@x.com", "google", "g-" + suffix));
  }

  private Integer storedStatusWithin(Duration timeout) {
    Instant deadline = Instant.now().plus(timeout);
    Integer status;
    do {
      status = hooks.findById(hook.getId()).orElseThrow().getLastStatusCode();
      if (status != null) return status;
      Thread.onSpinWait();
    } while (Instant.now().isBefore(deadline));
    return status;
  }
}
