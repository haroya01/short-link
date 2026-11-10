package com.example.short_link.link.webhook.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.example.short_link.common.net.HttpFetcher;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.LinkId;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.stats.application.ClickBuffer;
import com.example.short_link.link.stats.application.ClickContext;
import com.example.short_link.link.stats.application.ClickFlusher;
import com.example.short_link.link.stats.application.PendingClick;
import com.example.short_link.link.webhook.domain.LinkWebhookEntity;
import com.example.short_link.link.webhook.domain.WebhookFormat;
import com.example.short_link.link.webhook.domain.repository.LinkWebhookRepository;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@SpringBootTest
@ActiveProfiles("test")
class ClickWebhookDeliveryIntegrationTest {

  private static final String URL = "https://1.1.1.1/hook";

  @Autowired private UserRepository users;
  @Autowired private LinkRepository links;
  @Autowired private LinkWebhookRepository hooks;
  @Autowired private ClickBuffer buffer;
  @Autowired private ClickFlusher flusher;
  @Autowired private LinkWebhookDeliveryWorker worker;
  @Autowired private JdbcTemplate jdbc;
  @MockitoBean private HttpFetcher httpFetcher;

  private final List<String> bodies = new CopyOnWriteArrayList<>();
  private final List<Boolean> transactionOpenDuringHttp = new CopyOnWriteArrayList<>();
  private final Deque<Integer> answers = new ArrayDeque<>();
  private UserEntity owner;
  private LinkEntity link;
  private LinkWebhookEntity hook;

  @BeforeEach
  void aLinkWithAWebhook() {
    when(httpFetcher.fetch(any()))
        .thenAnswer(
            call -> {
              HttpFetcher.Request request = call.getArgument(0);
              if (request.pinned().uri().toString().equals(URL)) {
                bodies.add(new String(request.body(), StandardCharsets.UTF_8));
                transactionOpenDuringHttp.add(
                    TransactionSynchronizationManager.isActualTransactionActive());
              }
              Integer status = answers.isEmpty() ? 200 : answers.poll();
              return new HttpFetcher.Response(status, Map.of(), new byte[0]);
            });
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    owner = users.save(new UserEntity(suffix + "@x.com", "google", "g-" + suffix));
    link =
        links.save(
            new LinkEntity("https://example.com/" + suffix, "w" + suffix, owner.getId(), null));
    hook =
        hooks.save(new LinkWebhookEntity(link.linkId(), URL, "secret", "n", WebhookFormat.GENERIC));
  }

  @AfterEach
  void removeRows() {
    jdbc.update("DELETE FROM link_webhook WHERE id = ?", hook.getId());
    jdbc.update("DELETE FROM click_event WHERE link_id = ?", link.getId());
    jdbc.update("DELETE FROM link WHERE id = ?", link.getId());
    jdbc.update("DELETE FROM users WHERE id = ?", owner.getId());
  }

  @Test
  void aSavedClickIsQueuedWithItsIdAndReachesTheReceiverOutsideATransaction() {
    long clickId = saveClick();

    assertThat(delivery("status")).isEqualTo("PENDING");
    assertThat(delivery("click_id")).isEqualTo(clickId);

    worker.deliverDue();

    assertThat(bodies).singleElement().asString().contains("\"clickId\":" + clickId);
    assertThat(transactionOpenDuringHttp).containsExactly(false);
    assertThat(delivery("status")).isEqualTo("DONE");
    assertThat(hooks.findById(hook.getId()).orElseThrow().getLastStatusCode()).isEqualTo(200);
  }

  @Test
  void aReceiverThatFailsIsTriedAgainWithTheSameClickUntilItAccepts() {
    answers.add(503);
    long clickId = saveClick();

    worker.deliverDue();

    assertThat(delivery("status")).isEqualTo("PENDING");
    assertThat(delivery("last_status")).isEqualTo(503);
    assertThat(delivery("attempts")).isEqualTo(1);

    dueNow();
    worker.deliverDue();

    assertThat(delivery("status")).isEqualTo("DONE");
    assertThat(bodies).hasSize(2).allSatisfy(b -> assertThat(b).contains("\"clickId\":" + clickId));
  }

  @Test
  void aReceiverThatRefusesTheRequestIsNotTriedAgain() {
    answers.add(400);
    saveClick();

    worker.deliverDue();
    dueNow();
    worker.deliverDue();

    assertThat(delivery("status")).isEqualTo("GAVE_UP");
    assertThat(bodies).hasSize(1);
  }

  @Test
  void aBatchHookGetsTheSavedClicksInOneRequest() {
    LinkWebhookEntity batched = hooks.findById(hook.getId()).orElseThrow();
    batched.updateConfig(null, null, true, null, null, null);
    hooks.save(batched);
    for (int i = 0; i < 3; i++) buffer.offer(click());
    flusher.flush();

    worker.deliverDue();

    assertThat(bodies).singleElement().asString().contains("\"count\":3");
    assertThat(transactionOpenDuringHttp).containsExactly(false);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM link_webhook_delivery WHERE hook_id = ? AND status = 'DONE'",
                Integer.class,
                hook.getId()))
        .isEqualTo(3);
  }

  @Test
  void aWebhookSwitchedOffBeforeDeliveryDropsItsQueuedClicks() {
    saveClick();
    LinkWebhookEntity off = hooks.findById(hook.getId()).orElseThrow();
    off.disable();
    hooks.save(off);

    worker.deliverDue();

    assertThat(bodies).isEmpty();
    assertThat(delivery("status")).isEqualTo("GAVE_UP");
  }

  private long saveClick() {
    buffer.offer(click());
    flusher.flush();
    return jdbc.queryForObject(
        "SELECT id FROM click_event WHERE link_id = ? ORDER BY id DESC LIMIT 1",
        Long.class,
        link.getId());
  }

  private PendingClick click() {
    return new PendingClick(
        ClickContext.of(
            new LinkId(link.getId()),
            link.getOriginalUrl(),
            "https://news.example/a",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15",
            "198.51.100." + ThreadLocalRandom.current().nextInt(1, 255),
            "ko-KR"),
        null,
        Instant.now());
  }

  private Object delivery(String column) {
    return jdbc.queryForObject(
        "SELECT " + column + " FROM link_webhook_delivery WHERE hook_id = ?",
        Object.class,
        hook.getId());
  }

  private void dueNow() {
    jdbc.update(
        "UPDATE link_webhook_delivery SET next_attempt_at = UTC_TIMESTAMP(6) - INTERVAL 1 SECOND"
            + " WHERE hook_id = ?",
        hook.getId());
  }
}
