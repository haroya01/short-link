package com.example.short_link.link.webhook.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.example.short_link.common.net.HttpFetcher;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.webhook.application.helper.DailySummaryAssembler;
import com.example.short_link.link.webhook.domain.LinkWebhookEntity;
import com.example.short_link.link.webhook.domain.WebhookDeliveryMode;
import com.example.short_link.link.webhook.domain.WebhookFormat;
import com.example.short_link.link.webhook.domain.repository.LinkWebhookRepository;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
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

// Not @Transactional: the sweep's own transactions are what the test watches.
@SpringBootTest
@ActiveProfiles("test")
class DailyWebhookSummaryIsolationIntegrationTest {

  @Autowired private UserRepository users;
  @Autowired private LinkRepository links;
  @Autowired private LinkWebhookRepository hooks;
  @Autowired private DailyWebhookSummaryJob job;
  @Autowired private JdbcTemplate jdbc;
  @MockitoBean private HttpFetcher httpFetcher;
  @MockitoSpyBean private DailySummaryAssembler assembler;

  private final List<String> sent = new CopyOnWriteArrayList<>();
  private UserEntity owner;
  private LinkEntity working;
  private LinkEntity failing;
  private LinkWebhookEntity workingHook;
  private LinkWebhookEntity failingHook;
  private String workingUrl;

  @BeforeEach
  void twoLinksWhoseDailySummariesAreDue() {
    when(httpFetcher.fetch(any()))
        .thenAnswer(
            call -> {
              HttpFetcher.Request request = call.getArgument(0);
              sent.add(request.uri() + " " + where());
              return new HttpFetcher.Response(200, Map.of(), new byte[0]);
            });
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    owner = users.save(new UserEntity(suffix + "@x.com", "google", "g-" + suffix));
    working =
        links.save(
            new LinkEntity("https://example.com/a" + suffix, "a" + suffix, owner.getId(), null));
    failing =
        links.save(
            new LinkEntity("https://example.com/b" + suffix, "b" + suffix, owner.getId(), null));
    workingUrl = "https://1.1.1.1/hook-a-" + suffix;
    workingHook = hooks.save(dueEveryHour(working, workingUrl));
    failingHook = hooks.save(dueEveryHour(failing, "https://1.1.1.1/hook-b-" + suffix));
    doThrow(new IllegalStateException("this link's summary cannot be assembled"))
        .when(assembler)
        .assemble(eq(failing.linkId()), any(), any(), any());
  }

  private static LinkWebhookEntity dueEveryHour(LinkEntity link, String url) {
    LinkWebhookEntity hook =
        new LinkWebhookEntity(link.linkId(), url, "secret", "n", WebhookFormat.SLACK);
    hook.changeDeliveryMode(WebhookDeliveryMode.DAILY_SUMMARY, 0, null, null);
    return hook;
  }

  private static String where() {
    return TransactionSynchronizationManager.isActualTransactionActive()
        ? "inside a transaction"
        : "outside";
  }

  @AfterEach
  void removeRows() {
    jdbc.update(
        "DELETE FROM link_webhook WHERE id IN (?, ?)", workingHook.getId(), failingHook.getId());
    jdbc.update("DELETE FROM link WHERE user_id = ?", owner.getId());
    jdbc.update("DELETE FROM users WHERE id = ?", owner.getId());
  }

  private void sweepThroughAnyFailure() {
    try {
      job.sweep();
    } catch (RuntimeException ignored) {
    }
  }

  @Test
  void aHookWhoseSummaryFailsNeitherResendsNorStarvesTheOthers() {
    sweepThroughAnyFailure();
    sweepThroughAnyFailure();

    assertThat(sent)
        .filteredOn(call -> call.startsWith(workingUrl))
        .containsExactly(workingUrl + " outside");
    assertThat(hooks.findById(workingHook.getId()).orElseThrow().getSummaryLastSentDate())
        .isNotNull();
  }
}
