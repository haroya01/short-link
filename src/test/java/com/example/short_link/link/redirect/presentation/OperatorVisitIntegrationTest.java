package com.example.short_link.link.redirect.presentation;

import static com.example.short_link.support.TestCacheCleaner.clear;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.common.lock.RedisDistributedLock;
import com.example.short_link.common.notification.LinkNotice;
import com.example.short_link.common.notification.LinkNoticeKind;
import com.example.short_link.common.security.OperatorMarkerStore;
import com.example.short_link.link.application.dto.SavedClick;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.notice.application.LinkClickNoticeListener;
import com.example.short_link.link.notice.application.LinkNoticeProperties;
import com.example.short_link.link.stats.application.ClickBuffer;
import com.example.short_link.link.stats.application.ClickFlusher;
import com.example.short_link.link.stats.application.read.ClickRangeReader;
import com.example.short_link.link.stats.application.read.ClickTotalsReader;
import com.example.short_link.link.webhook.domain.LinkWebhookEntity;
import com.example.short_link.link.webhook.domain.repository.LinkWebhookRepository;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@RecordApplicationEvents
@Tag("integration")
class OperatorVisitIntegrationTest {

  private static final String DESKTOP_SAFARI =
      "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko)"
          + " Version/17.4 Safari/605.1.15";
  private static final String DESTINATION = "https://example.com/landing";

  enum NotAnOperator {
    NO_COOKIE,
    MALFORMED_COOKIE,
    NEVER_ISSUED,
    REVOKED,
    EXPIRED
  }

  private record ClickRow(boolean bot, String botName) {}

  @Autowired private MockMvc mvc;
  @Autowired private UserRepository users;
  @Autowired private LinkRepository links;
  @Autowired private ClickBuffer clickBuffer;
  @Autowired private ClickFlusher clickFlusher;
  @Autowired private ClickTotalsReader totals;
  @Autowired private ClickRangeReader ranges;
  @Autowired private LinkNoticeProperties noticeProperties;
  @Autowired private RedisDistributedLock cooldown;
  @Autowired private OperatorMarkerStore markers;
  @Autowired private LinkWebhookRepository hooks;
  @Autowired private CacheManager cacheManager;
  @Autowired private EntityManager entityManager;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ApplicationEvents events;

  private final List<String> issued = new ArrayList<>();
  private final String clientIp =
      "198.18."
          + ThreadLocalRandom.current().nextInt(256)
          + "."
          + ThreadLocalRandom.current().nextInt(1, 255);
  private Long ownerId;
  private String code;

  @BeforeEach
  void anOwner() {
    clickBuffer.drain(Integer.MAX_VALUE);
    clear(cacheManager, "link");
    String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    ownerId =
        users
            .save(new UserEntity("owner-" + suffix + "@example.com", "google", "g-op-" + suffix))
            .getId();
    code = "op" + suffix;
  }

  @AfterEach
  void forgetMarkers() {
    issued.forEach(markers::revoke);
    clickBuffer.drain(Integer.MAX_VALUE);
  }

  @Test
  void anOperatorsVisitToAnUnlimitedLinkRedirectsButIsKeptAsAPreviewAndTellsTheOwnerNothing()
      throws Exception {
    LinkEntity link = aLink(null);

    visit(marker())
        .andExpect(status().isFound())
        .andExpect(header().string("Location", DESTINATION));
    clickFlusher.flush();

    assertThat(clickRows(link)).containsExactly(new ClickRow(true, "preview:operator"));
    assertThat(totals.countHumanByLinkId(link.getId())).isZero();
    assertThat(noticesToTheOwner()).isEmpty();
  }

  @Test
  void anOperatorsVisitToAViewLimitedLinkSpendsAViewAndCountsAsAPersonsClick() throws Exception {
    LinkEntity link = aLink(2);

    visit(marker())
        .andExpect(status().isFound())
        .andExpect(header().string("Location", DESTINATION));
    clickFlusher.flush();

    assertThat(viewsSpent(link)).isEqualTo(1);
    assertThat(clickRows(link)).containsExactly(new ClickRow(false, null));
    assertThat(totals.countHumanByLinkId(link.getId())).isEqualTo(1);
    assertThat(noticesToTheOwner())
        .extracting(LinkNotice::kind)
        .containsExactly(LinkNoticeKind.FIRST_CLICK);
  }

  @Test
  void aMarkerDoesNotOpenAViewLimitedLinkWhoseViewsAreSpent() throws Exception {
    LinkEntity link = aLink(1);
    String marker = marker();

    visit(marker).andExpect(status().isFound());
    visit(marker).andExpect(status().isGone()).andExpect(header().doesNotExist("Location"));

    assertThat(viewsSpent(link)).isEqualTo(1);
  }

  @Test
  void anOperatorsVisitQueuesAWebhookOnlyForAHookThatAskedForBots() throws Exception {
    LinkEntity link = aLink(null);
    LinkWebhookEntity peopleOnly = aHook(link, false);
    LinkWebhookEntity botsToo = aHook(link, true);

    visit(marker()).andExpect(status().isFound());
    clickFlusher.flush();

    assertThat(queuedPayloads(peopleOnly)).isEmpty();
    assertThat(queuedPayloads(botsToo)).singleElement().asString().contains("\"bot\":true");
  }

  @Test
  void aPersonsVisitQueuesAWebhookForEveryHook() throws Exception {
    LinkEntity link = aLink(null);
    LinkWebhookEntity peopleOnly = aHook(link, false);
    LinkWebhookEntity botsToo = aHook(link, true);

    visit(null).andExpect(status().isFound());
    clickFlusher.flush();

    assertThat(queuedPayloads(peopleOnly)).singleElement().asString().contains("\"bot\":false");
    assertThat(queuedPayloads(botsToo)).singleElement().asString().contains("\"bot\":false");
  }

  @ParameterizedTest
  @EnumSource(NotAnOperator.class)
  void aVisitWithoutAMarkerTheStoreHoldsCountsAsAPersonsClick(NotAnOperator visitor)
      throws Exception {
    LinkEntity link = aLink(null);

    visit(cookieOf(visitor))
        .andExpect(status().isFound())
        .andExpect(header().string("Location", DESTINATION));
    clickFlusher.flush();

    assertThat(clickRows(link)).containsExactly(new ClickRow(false, null));
    assertThat(totals.countHumanByLinkId(link.getId())).isEqualTo(1);
    assertThat(noticesToTheOwner())
        .extracting(LinkNotice::kind)
        .containsExactly(LinkNoticeKind.FIRST_CLICK);
  }

  private String cookieOf(NotAnOperator visitor) {
    return switch (visitor) {
      case NO_COOKIE -> null;
      case MALFORMED_COOKIE -> "operator";
      case NEVER_ISSUED -> "Zm9yLXRoZS1vcGVyYXRvci1tYXJrZXIt";
      case REVOKED -> {
        String revoked = marker();
        markers.revoke(revoked);
        yield revoked;
      }
      case EXPIRED -> {
        String expired = markers.issue(Duration.ofMillis(50));
        issued.add(expired);
        await().atMost(Duration.ofSeconds(5)).until(() -> !markers.isValid(expired));
        yield expired;
      }
    };
  }

  private LinkEntity aLink(Integer maxViews) {
    LinkEntity link = new LinkEntity(DESTINATION, code, ownerId, null);
    link.setMaxViews(maxViews);
    return links.save(link);
  }

  private LinkWebhookEntity aHook(LinkEntity link, boolean includeBots) {
    LinkWebhookEntity hook =
        new LinkWebhookEntity(link.linkId(), "https://1.1.1.1/hook", "secret", "hook");
    hook.updateConfig(includeBots, null, null, null, null, null);
    return hooks.save(hook);
  }

  private List<String> queuedPayloads(LinkWebhookEntity hook) {
    entityManager.flush();
    return jdbc.queryForList(
        "SELECT payload FROM link_webhook_delivery WHERE hook_id = ? AND status = 'PENDING'",
        String.class,
        hook.getId());
  }

  private String marker() {
    String marker = markers.issue(Duration.ofMinutes(5));
    issued.add(marker);
    return marker;
  }

  private ResultActions visit(String markerCookie) throws Exception {
    MockHttpServletRequestBuilder request =
        get("/" + code)
            .header("User-Agent", DESKTOP_SAFARI)
            .header("Accept-Language", "ko-KR,ko;q=0.9")
            .with(
                servletRequest -> {
                  servletRequest.setRemoteAddr(clientIp);
                  return servletRequest;
                });
    if (markerCookie != null) {
      request.cookie(new Cookie(OperatorMarkerStore.COOKIE_NAME, markerCookie));
    }
    return mvc.perform(request);
  }

  private List<ClickRow> clickRows(LinkEntity link) {
    entityManager.flush();
    return jdbc.query(
        "SELECT is_bot, bot_name FROM click_event WHERE link_id = ? ORDER BY id",
        (row, index) -> new ClickRow(row.getBoolean("is_bot"), row.getString("bot_name")),
        link.getId());
  }

  private int viewsSpent(LinkEntity link) {
    entityManager.flush();
    return jdbc.queryForObject(
        "SELECT view_count FROM link WHERE id = ?", Integer.class, link.getId());
  }

  private List<LinkNotice> noticesToTheOwner() {
    List<LinkNotice> sent = new ArrayList<>();
    LinkClickNoticeListener afterCommit =
        new LinkClickNoticeListener(links, totals, ranges, sent::add, noticeProperties, cooldown);
    events.stream(SavedClick.class).forEach(afterCommit::onClickRecorded);
    return sent;
  }
}
