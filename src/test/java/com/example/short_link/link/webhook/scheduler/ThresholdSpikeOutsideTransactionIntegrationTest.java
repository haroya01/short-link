package com.example.short_link.link.webhook.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import com.example.short_link.common.net.HttpFetcher;
import com.example.short_link.link.application.dto.ClickRecordedEvent;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.stats.application.read.ClickTotalsReader;
import com.example.short_link.link.webhook.domain.LinkWebhookEntity;
import com.example.short_link.link.webhook.domain.WebhookDeliveryMode;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

// Not @Transactional: the detector's own transaction is what the test watches.
@SpringBootTest
@ActiveProfiles("test")
class ThresholdSpikeOutsideTransactionIntegrationTest {

  @Autowired private UserRepository users;
  @Autowired private LinkRepository links;
  @Autowired private LinkWebhookRepository hooks;
  @Autowired private ThresholdSpikeDetector detector;
  @Autowired private JdbcTemplate jdbc;
  @MockitoBean private HttpFetcher httpFetcher;
  @MockitoSpyBean private ClickTotalsReader clickTotals;

  private final List<String> sent = new CopyOnWriteArrayList<>();
  private UserEntity owner;
  private LinkEntity link;
  private LinkWebhookEntity hook;

  @BeforeEach
  void aLinkWhoseSpikeAlertIsAboutToFire() {
    when(httpFetcher.fetch(any()))
        .thenAnswer(
            call -> {
              sent.add(where());
              return new HttpFetcher.Response(200, Map.of(), new byte[0]);
            });
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    owner = users.save(new UserEntity(suffix + "@x.com", "google", "g-" + suffix));
    link =
        links.save(
            new LinkEntity("https://example.com/" + suffix, "s" + suffix, owner.getId(), null));
    LinkWebhookEntity spiking =
        new LinkWebhookEntity(
            link.linkId(), "https://1.1.1.1/spike-" + suffix, "secret", "n", WebhookFormat.SLACK);
    spiking.changeDeliveryMode(WebhookDeliveryMode.THRESHOLD_SPIKE, null, 3, 60);
    hook = hooks.save(spiking);
    doReturn(5L).when(clickTotals).countSinceByLinkId(eq(link.linkId().value()), any());
  }

  private static String where() {
    return TransactionSynchronizationManager.isActualTransactionActive()
        ? "inside a transaction"
        : "outside";
  }

  @AfterEach
  void removeRows() {
    jdbc.update("DELETE FROM link_webhook WHERE id = ?", hook.getId());
    jdbc.update("DELETE FROM link WHERE id = ?", link.getId());
    jdbc.update("DELETE FROM users WHERE id = ?", owner.getId());
  }

  @Test
  void aSpikeAlertIsSentOutsideATransactionAndItsCooldownIsStored() {
    detector.onClickRecorded(
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

    assertThat(firedWithin(Duration.ofSeconds(10))).isNotNull();
    assertThat(sent).containsExactly("outside");
  }

  private Instant firedWithin(Duration timeout) {
    Instant deadline = Instant.now().plus(timeout);
    Instant fired;
    do {
      fired = hooks.findById(hook.getId()).orElseThrow().getSpikeLastFiredAt();
      if (fired != null) return fired;
      Thread.onSpinWait();
    } while (Instant.now().isBefore(deadline));
    return fired;
  }
}
