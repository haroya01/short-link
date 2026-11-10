package com.example.short_link.link.stats.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
import java.util.stream.LongStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Tag("integration")
class AdminLinkClicksIntegrationTest {

  private static final String CLICKS = "/api/v1/admin/links/admclk01/clicks";

  @Autowired private MockMvc mvc;
  @Autowired private LinkRepository links;
  @Autowired private ClickEventRepository clicks;
  @Autowired private UserRepository users;
  @Autowired private JwtTokenService jwt;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private JsonMapper json;

  @Autowired
  @Qualifier("requestMappingHandlerMapping")
  private RequestMappingHandlerMapping handlers;

  private LinkEntity link;
  private String admin;
  private String member;

  @BeforeEach
  void anAdminAMemberAndTheMembersLink() {
    UserEntity operator = users.save(new UserEntity("clk-admin@x.com", "google", "g-clk-admin"));
    operator.promoteToAdmin();
    users.save(operator);
    UserEntity owner = users.save(new UserEntity("clk-owner@x.com", "google", "g-clk-owner"));
    admin = "Bearer " + jwt.createAccessToken(operator.getId(), "ADMIN");
    member = "Bearer " + jwt.createAccessToken(owner.getId(), "USER");
    link = links.save(new LinkEntity("https://example.com", "admclk01", owner.getId(), null));
  }

  @AfterTransaction
  void removeTheAuditRowsThatOutliveTheRollback() {
    jdbc.update("DELETE FROM audit_log WHERE target_type = 'link' AND target_id = 'admclk01'");
  }

  @Test
  void everyRouteOfTheControllerRefusesAMemberAndAVisitorLikeTheOtherAdminRoutes()
      throws Exception {
    long clickId = click(Instant.now(), false, null).getId();
    List<MockHttpServletRequestBuilder> member = routes(clickId, this.member);
    List<MockHttpServletRequestBuilder> visitor = routes(clickId, null);
    assertThat(member).hasSize(3);

    mvc.perform(get("/api/v1/admin/links/admclk01").header("Authorization", this.member))
        .andExpect(status().isForbidden());
    for (MockHttpServletRequestBuilder request : member) {
      mvc.perform(request).andExpect(status().isForbidden());
    }
    mvc.perform(get("/api/v1/admin/links/admclk01")).andExpect(status().isUnauthorized());
    for (MockHttpServletRequestBuilder request : visitor) {
      mvc.perform(request).andExpect(status().isUnauthorized());
    }
    assertThat(
            jdbc.queryForObject(
                "SELECT is_bot FROM click_event WHERE id = ?", Boolean.class, clickId))
        .isFalse();
  }

  private List<MockHttpServletRequestBuilder> routes(long clickId, String bearer) {
    List<MockHttpServletRequestBuilder> requests = new ArrayList<>();
    handlers
        .getHandlerMethods()
        .forEach(
            (mapping, handler) -> {
              if (handler.getBeanType() != AdminLinkClicksController.class) return;
              String path =
                  mapping
                      .getPathPatternsCondition()
                      .getPatternValues()
                      .iterator()
                      .next()
                      .replace("{code}", "admclk01");
              RequestMethod method = mapping.getMethodsCondition().getMethods().iterator().next();
              MockHttpServletRequestBuilder request =
                  request(HttpMethod.valueOf(method.name()), path)
                      .contentType(MediaType.APPLICATION_JSON)
                      .content("{\"clickIds\":[" + clickId + "]}");
              requests.add(bearer == null ? request : request.header("Authorization", bearer));
            });
    return requests;
  }

  @Test
  void theListCarriesEachClicksIdWhetherItIsExcludedAndTheOwnersFields() throws Exception {
    Instant at = Instant.now().minus(Duration.ofHours(3));
    long human = click(at, false, null).getId();
    long operator = click(at.plusSeconds(60), true, "preview:operator").getId();
    long excluded = click(at.plusSeconds(120), true, "preview:excluded").getId();

    JsonNode page = page(CLICKS);

    assertThat(ids(page)).containsExactly(excluded, operator, human);
    assertThat(page.get("items").valueStream().map(item -> item.get("excluded").asBoolean()))
        .containsExactly(true, false, false);
    JsonNode excludedClick = page.get("items").get(0).get("click");
    assertThat(excludedClick.get("bot").asBoolean()).isTrue();
    assertThat(excludedClick.get("botName").asString()).isEqualTo("preview:excluded");
    JsonNode humanClick = page.get("items").get(2).get("click");
    assertThat(humanClick.get("bot").asBoolean()).isFalse();
    assertThat(humanClick.get("country").asString()).isEqualTo("KR");
    assertThat(humanClick.get("device").asString()).isEqualTo("desktop");
    assertThat(humanClick.get("channel").asString()).isEqualTo("direct");
    assertThat(humanClick.get("ipMasked").asString()).isEqualTo("1.2.3.*");
    assertThat(Instant.parse(humanClick.get("clickedAt").asString()))
        .isEqualTo(at.truncatedTo(ChronoUnit.SECONDS));
    assertThat(page.get("nextCursor").isNull()).isTrue();
  }

  @Test
  void theListPagesBackwardsByCursorUntilTheClicksRunOut() throws Exception {
    Instant at = Instant.now().minus(Duration.ofHours(3));
    long first = click(at, false, null).getId();
    long second = click(at.plusSeconds(1), false, null).getId();
    long third = click(at.plusSeconds(2), false, null).getId();

    JsonNode newest = page(CLICKS + "?limit=2");
    JsonNode oldest = page(CLICKS + "?limit=2&cursor=" + newest.get("nextCursor").asString());

    assertThat(ids(newest)).containsExactly(third, second);
    assertThat(ids(oldest)).containsExactly(first);
    assertThat(oldest.get("nextCursor").isNull()).isTrue();
  }

  @Test
  void theListEndsWhereClicksWereFoldedIntoTheRollups() throws Exception {
    Instant boundary = Instant.now().minus(Duration.ofDays(90)).truncatedTo(ChronoUnit.DAYS);
    click(boundary.minusSeconds(2), false, null);
    click(boundary.minusSeconds(1), false, null);
    long kept = click(boundary, false, null).getId();
    long newest = click(boundary.plusSeconds(1), false, null).getId();
    jdbc.update(
        "UPDATE click_rollup_state SET rolled_until = FROM_UNIXTIME(?),"
            + " aggregated_until = FROM_UNIXTIME(?) WHERE id = 1",
        boundary.getEpochSecond(),
        boundary.getEpochSecond());

    JsonNode page = page(CLICKS + "?limit=3");

    assertThat(ids(page)).containsExactly(newest, kept);
    assertThat(page.get("nextCursor").isNull()).isTrue();
  }

  @Test
  void anEmptySelectionIsRefused() throws Exception {
    mvc.perform(selection("/exclude", admin, List.of())).andExpect(status().isBadRequest());
    mvc.perform(selection("/restore", admin, List.of())).andExpect(status().isBadRequest());
    mvc.perform(
            post(CLICKS + "/exclude")
                .header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"clickIds\":[null]}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void aSelectionLargerThanOneListPageIsRefused() throws Exception {
    List<Long> onePage = LongStream.rangeClosed(1, 200).boxed().toList();
    List<Long> oneMore = LongStream.rangeClosed(1, 201).boxed().toList();

    mvc.perform(selection("/exclude", admin, oneMore)).andExpect(status().isBadRequest());
    mvc.perform(selection("/restore", admin, oneMore)).andExpect(status().isBadRequest());
    mvc.perform(selection("/restore", admin, onePage))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.changed").value(0));
  }

  @Test
  void anUnknownLinkIsNotFound() throws Exception {
    mvc.perform(get("/api/v1/admin/links/nolink404/clicks").header("Authorization", admin))
        .andExpect(status().isNotFound());
    mvc.perform(
            post("/api/v1/admin/links/nolink404/clicks/exclude")
                .header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"clickIds\":[1]}"))
        .andExpect(status().isNotFound());
  }

  private MockHttpServletRequestBuilder selection(String action, String bearer, List<Long> ids) {
    return post(CLICKS + action)
        .header("Authorization", bearer)
        .contentType(MediaType.APPLICATION_JSON)
        .content(
            "{\"clickIds\":["
                + ids.stream().map(String::valueOf).collect(Collectors.joining(","))
                + "]}");
  }

  private JsonNode page(String path) throws Exception {
    MvcResult result =
        mvc.perform(get(path).header("Authorization", admin))
            .andExpect(status().isOk())
            .andReturn();
    return json.readTree(result.getResponse().getContentAsString());
  }

  private static List<Long> ids(JsonNode page) {
    return page.get("items").valueStream().map(item -> item.get("id").asLong()).toList();
  }

  private ClickEventEntity click(Instant at, boolean bot, String botName) {
    return clicks.save(
        ClickEventEntity.builder()
            .linkId(link.linkId())
            .clickedAt(at)
            .userAgent("ua")
            .clientIp("1.2.3.4")
            .deviceClass("desktop")
            .countryCode("KR")
            .bot(bot)
            .botName(botName)
            .build());
  }
}
