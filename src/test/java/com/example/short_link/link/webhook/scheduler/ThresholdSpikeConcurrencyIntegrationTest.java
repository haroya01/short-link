package com.example.short_link.link.webhook.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.example.short_link.link.application.dto.ClickRecordedEvent;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.LinkId;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.stats.application.read.ClickTotalsReader;
import com.example.short_link.link.stats.domain.ClickEventEntity;
import com.example.short_link.link.stats.domain.repository.ClickAlertReadRepository;
import com.example.short_link.link.stats.domain.repository.ClickEventRepository;
import com.example.short_link.link.webhook.domain.LinkWebhookEntity;
import com.example.short_link.link.webhook.domain.WebhookDeliveryMode;
import com.example.short_link.link.webhook.domain.repository.LinkWebhookRepository;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
@Tag("integration")
class ThresholdSpikeConcurrencyIntegrationTest {

  private static final int CLICKS = 8;

  @Autowired private LinkWebhookRepository hooks;
  @Autowired private LinkRepository links;
  @Autowired private ClickTotalsReader clickTotals;
  @Autowired private ClickAlertReadRepository clickAlerts;
  @Autowired private ClickEventRepository clickEvents;
  @Autowired private UserRepository users;
  @Autowired private TransactionTemplate transaction;
  @Autowired private JdbcTemplate jdbc;

  private final WebhookHttpDeliveryClient delivery = mock(WebhookHttpDeliveryClient.class);
  private ThresholdSpikeDetector detector;
  private Long userId;
  private Long linkId;
  private Long hookId;

  @BeforeEach
  void aSpikingLinkWithASpikeHook() {
    detector =
        new ThresholdSpikeDetector(
            hooks, links, clickTotals, clickAlerts, delivery, Clock.systemUTC(), transaction);
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    userId =
        users.save(new UserEntity("spike-" + suffix + "@x.com", "google", "g-s" + suffix)).getId();
    String code = "s" + suffix.substring(0, 7);
    linkId = links.save(new LinkEntity("https://example.com/" + code, code, userId, null)).getId();
    LinkWebhookEntity hook =
        new LinkWebhookEntity(new LinkId(linkId), "https://hooks.example/spike", "secret", "s");
    hook.changeDeliveryMode(WebhookDeliveryMode.THRESHOLD_SPIKE, null, 3, 10);
    hookId = hooks.save(hook).getId();
    List<ClickEventEntity> burst = new ArrayList<>();
    for (int i = 0; i < CLICKS; i++) {
      burst.add(
          ClickEventEntity.builder().linkId(new LinkId(linkId)).clickedAt(Instant.now()).build());
    }
    clickEvents.saveAll(burst);
  }

  @AfterEach
  void removeSeed() {
    jdbc.update("DELETE FROM link_webhook WHERE id = ?", hookId);
    jdbc.update("DELETE FROM click_event WHERE link_id = ?", linkId);
    jdbc.update("DELETE FROM link WHERE id = ?", linkId);
    jdbc.update("DELETE FROM users WHERE id = ?", userId);
  }

  @Test
  void clicksLandingTogetherSendOneSpikeAlert() throws Exception {
    doAnswer(
            inv -> {
              Thread.sleep(300);
              return null;
            })
        .when(delivery)
        .deliver(any(), any());
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(CLICKS);
    try {
      List<Future<?>> done = new ArrayList<>();
      for (int i = 0; i < CLICKS; i++) {
        done.add(
            pool.submit(
                () -> {
                  start.await();
                  detector.onClickRecorded(click());
                  return null;
                }));
      }
      start.countDown();
      for (Future<?> each : done) each.get(30, TimeUnit.SECONDS);
    } finally {
      pool.shutdownNow();
    }

    verify(delivery, times(1)).deliver(any(), any());
    assertThat(hooks.findById(hookId).orElseThrow().getSpikeLastFiredAt()).isNotNull();
  }

  private ClickRecordedEvent click() {
    return new ClickRecordedEvent(
        new LinkId(linkId), null, userId, Instant.now(), "KR", "mobile", null, false, null);
  }
}
