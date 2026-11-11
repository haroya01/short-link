package com.example.short_link.admin.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.stats.domain.ClickEventEntity;
import com.example.short_link.link.stats.domain.repository.ClickEventRepository;
import com.example.short_link.user.application.JwtTokenService;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Tag("integration")
class AdminLinkHumanClicksIntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private LinkRepository links;
  @Autowired private ClickEventRepository clicks;
  @Autowired private UserRepository users;
  @Autowired private JwtTokenService jwt;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private JsonMapper json;

  private Long ownerId;
  private String admin;

  @BeforeEach
  void anAdminAndALinkOwner() {
    UserEntity operator = users.save(new UserEntity("hm-admin@x.com", "google", "g-hm-admin"));
    operator.promoteToAdmin();
    users.save(operator);
    ownerId = users.save(new UserEntity("hm-owner@x.com", "google", "g-hm-owner")).getId();
    admin = "Bearer " + jwt.createAccessToken(operator.getId(), "ADMIN");
  }

  @AfterTransaction
  void removeTheAuditRowsThatOutliveTheRollback() {
    jdbc.update("DELETE FROM audit_log WHERE target_type = 'link' AND target_id LIKE 'hmclk%'");
  }

  @Test
  void theRowCountsPeopleApartFromCrawlersAndPreviews() throws Exception {
    LinkEntity link = link("hmclk001", "https://people.example.com/a");
    for (int i = 0; i < 3; i++) click(link, Instant.now(), null);
    for (int i = 0; i < 2; i++) click(link, Instant.now(), "crawler:x");
    click(link, Instant.now(), "preview:slackbot");
    click(link, Instant.now(), "preview:operator");

    JsonNode row = listed("hmclk001");
    JsonNode detail = detail("hmclk001");

    assertThat(counts(row)).containsExactly(3L, 7L);
    assertThat(counts(detail.get("meta"))).containsExactly(3L, 7L);
    assertThat(detail.get("stats").get("humanClicks").asLong()).isEqualTo(3);
    assertThat(detail.get("stats").get("totalClicks").asLong()).isEqualTo(7);
  }

  @Test
  void excludingClicksLowersOnlyThePeopleCountAndRestoringBringsItBack() throws Exception {
    LinkEntity link = link("hmclk002", "https://people.example.com/b");
    List<Long> people = new ArrayList<>();
    for (int i = 0; i < 4; i++) people.add(click(link, Instant.now(), null));
    click(link, Instant.now(), "crawler:x");
    List<Long> picked = people.subList(0, 2);
    assertThat(counts(listed("hmclk002"))).containsExactly(4L, 5L);

    selection("hmclk002", "exclude", picked);

    assertThat(counts(listed("hmclk002"))).containsExactly(2L, 5L);
    assertThat(counts(detail("hmclk002").get("meta"))).containsExactly(2L, 5L);

    selection("hmclk002", "restore", picked);

    assertThat(counts(listed("hmclk002"))).containsExactly(4L, 5L);
    assertThat(counts(detail("hmclk002").get("meta"))).containsExactly(4L, 5L);
  }

  @Test
  void clicksFoldedIntoHourlyRowsAreAddedOnBothSidesAndTheirRawRowsAreNotCountedAgain()
      throws Exception {
    LinkEntity link = link("hmclk003", "https://people.example.com/c");
    Instant rolledUntil = Instant.now().minus(Duration.ofDays(90)).truncatedTo(ChronoUnit.DAYS);
    Instant foldedHour = rolledUntil.minus(Duration.ofHours(2));
    click(link, foldedHour.plusSeconds(60), null);
    click(link, foldedHour.plusSeconds(90), "crawler:x");
    for (int i = 0; i < 2; i++) click(link, Instant.now(), null);
    click(link, Instant.now(), "crawler:x");
    jdbc.update(
        "INSERT INTO click_hourly (link_id, hour_start, age_day, humans, bots, first_human_at,"
            + " last_human_at, last_at) VALUES (?, FROM_UNIXTIME(?), 0, 4, 3, FROM_UNIXTIME(?),"
            + " FROM_UNIXTIME(?), FROM_UNIXTIME(?))",
        link.getId(),
        foldedHour.getEpochSecond(),
        foldedHour.getEpochSecond(),
        foldedHour.getEpochSecond() + 60,
        foldedHour.getEpochSecond() + 90);
    jdbc.update(
        "UPDATE click_rollup_state SET rolled_until = FROM_UNIXTIME(?),"
            + " aggregated_until = FROM_UNIXTIME(?) WHERE id = 1",
        rolledUntil.getEpochSecond(),
        rolledUntil.getEpochSecond());

    assertThat(counts(listed("hmclk003"))).containsExactly(6L, 10L);
    assertThat(counts(detail("hmclk003").get("meta"))).containsExactly(6L, 10L);
  }

  @Test
  void sortingByClicksRanksByPeopleThenByAllClicks() throws Exception {
    LinkEntity crawled = link("hmclkbot1", "https://hmsort.example.com/crawled");
    LinkEntity popular = link("hmclkppl1", "https://hmsort.example.com/popular");
    LinkEntity quiet = link("hmclkone1", "https://hmsort.example.com/quiet");
    createdDaysAgo(crawled, 3);
    createdDaysAgo(popular, 2);
    createdDaysAgo(quiet, 1);
    click(crawled, Instant.now(), null);
    for (int i = 0; i < 5; i++) click(crawled, Instant.now(), "crawler:x");
    for (int i = 0; i < 2; i++) click(popular, Instant.now(), null);
    click(quiet, Instant.now(), null);

    JsonNode items = page("/api/v1/admin/links?q=hmsort.example&sort=clicks").get("items");

    assertThat(items.valueStream().map(item -> item.get("shortCode").asString()))
        .containsExactly("hmclkppl1", "hmclkbot1", "hmclkone1");
    assertThat(items.valueStream().map(AdminLinkHumanClicksIntegrationTest::counts))
        .containsExactly(List.of(2L, 2L), List.of(1L, 6L), List.of(1L, 1L));
  }

  private static List<Long> counts(JsonNode row) {
    return List.of(row.get("humanClickCount").asLong(), row.get("clickCount").asLong());
  }

  private JsonNode listed(String code) throws Exception {
    JsonNode items = page("/api/v1/admin/links?q=" + code).get("items");
    assertThat(items).hasSize(1);
    return items.get(0);
  }

  private JsonNode detail(String code) throws Exception {
    return page("/api/v1/admin/links/" + code);
  }

  private JsonNode page(String path) throws Exception {
    return json.readTree(
        mvc.perform(get(path).header("Authorization", admin))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private void selection(String code, String action, List<Long> clickIds) throws Exception {
    String body =
        mvc.perform(
                post("/api/v1/admin/links/" + code + "/clicks/" + action)
                    .header("Authorization", admin)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"clickIds\":["
                            + clickIds.stream()
                                .map(String::valueOf)
                                .collect(Collectors.joining(","))
                            + "]}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(json.readTree(body).get("changed").asInt()).isEqualTo(clickIds.size());
  }

  private LinkEntity link(String code, String url) {
    return links.save(new LinkEntity(url, code, ownerId, null));
  }

  private void createdDaysAgo(LinkEntity link, int days) {
    jdbc.update(
        "UPDATE link SET created_at = TIMESTAMPADD(DAY, ?, created_at) WHERE id = ?",
        -days,
        link.getId());
  }

  private long click(LinkEntity link, Instant at, String botName) {
    return clicks
        .save(
            ClickEventEntity.builder()
                .linkId(link.linkId())
                .clickedAt(at)
                .userAgent("ua")
                .clientIp("1.2.3.4")
                .deviceClass("desktop")
                .bot(botName != null)
                .botName(botName)
                .build())
        .getId();
  }
}
