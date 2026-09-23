package com.example.short_link.link.webhook.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.example.short_link.common.net.HttpFetcher;
import com.example.short_link.link.application.dto.ClickRecordedEvent;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.webhook.domain.LinkWebhookEntity;
import com.example.short_link.link.webhook.domain.WebhookFormat;
import com.example.short_link.link.webhook.domain.repository.LinkWebhookRepository;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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
class LinkWebhookHttpOutsideTransactionTest {
  private static final String URL = "https://1.1.1.1/hook";

  @Autowired private UserRepository users;
  @Autowired private LinkRepository links;
  @Autowired private LinkWebhookRepository hooks;
  @Autowired private LinkWebhookDispatcher dispatcher;
  @Autowired private WebhookBatchBuffer batchBuffer;
  @Autowired private WebhookBatchDeliverer batchDeliverer;
  @MockitoBean private HttpFetcher httpFetcher;

  private final List<Boolean> transactionOpenDuringHttp = new CopyOnWriteArrayList<>();
  private UserEntity owner;
  private LinkEntity link;
  private LinkWebhookEntity hook;

  @BeforeEach
  void aLinkWithAWebhook() {
    when(httpFetcher.fetch(any()))
        .thenAnswer(
            call -> {
              transactionOpenDuringHttp.add(
                  TransactionSynchronizationManager.isActualTransactionActive());
              return new HttpFetcher.Response(200, Map.of(), new byte[0]);
            });
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    owner = users.save(new UserEntity(suffix + "@x.com", "google", "g-" + suffix));
    link =
        links.save(
            new LinkEntity("https://example.com/" + suffix, "w" + suffix, owner.getId(), null));
    hook =
        hooks.save(new LinkWebhookEntity(link.linkId(), URL, "secret", "n", WebhookFormat.SLACK));
  }

  @AfterEach
  void removeRows() {
    hooks.delete(hooks.findById(hook.getId()).orElseThrow());
    links.delete(link);
  }

  @Test
  void aClickWebhookIsSentWithoutAnOpenTransactionAndItsOutcomeIsStored() {
    dispatcher.onClickRecorded(
        new ClickRecordedEvent(
            link.linkId(),
            link.getShortCode().value(),
            owner.getId(),
            Instant.now(),
            "KR",
            "Desktop",
            null,
            false,
            null));

    assertThat(storedStatusWithin(Duration.ofSeconds(10))).isEqualTo(200);
    assertThat(transactionOpenDuringHttp).containsExactly(false);
  }

  @Test
  void aBatchWebhookIsSentWithoutAnOpenTransactionAndItsOutcomeIsStored() {
    LinkWebhookEntity batched = hooks.findById(hook.getId()).orElseThrow();
    batched.updateConfig(null, null, true, null, null, null);
    hooks.save(batched);
    batchBuffer.enqueue(hook.getId(), Map.of("type", "click"));

    batchDeliverer.deliverOne(hook.getId());

    assertThat(storedStatusWithin(Duration.ZERO)).isEqualTo(200);
    assertThat(transactionOpenDuringHttp).containsExactly(false);
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
