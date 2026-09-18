package com.example.short_link.link.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.stats.domain.ClickEventEntity;
import com.example.short_link.link.stats.domain.repository.ClickEventRepository;
import com.example.short_link.user.application.JwtTokenService;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class MyLinksWorkspaceIntegrationTest extends com.example.short_link.testsupport.DockerHttpTest {
  private static final Instant NOW = Instant.parse("2026-09-13T00:30:00Z");
  @Autowired MockMvc mvc;
  @Autowired LinkRepository links;
  @Autowired ClickEventRepository clicks;
  @Autowired UserRepository users;
  @Autowired JwtTokenService jwt;
  @Autowired EntityManager em;
  @MockitoBean Clock clock;
  UserEntity owner;
  UserEntity other;
  String token;

  @BeforeEach
  void setup() {
    when(clock.instant()).thenReturn(NOW);
    owner = users.save(new UserEntity("workspace-owner@example.com", "google", "workspace-owner"));
    owner.changeTimezone("Asia/Kathmandu");
    other = users.save(new UserEntity("workspace-other@example.com", "google", "workspace-other"));
    token = "Bearer " + jwt.createAccessToken(owner.getId(), "USER");
  }

  @Test
  void favoritesStayOrderedAndReachableBeyondEightyLinksAndAreDeletedWithTheirLink()
      throws Exception {
    List<LinkEntity> many = makeLinks(85);
    many.getFirst().updateNote("오래 쓰는 링크");
    putFavorite("work000");
    putFavorite("work084");
    putFavorite("work000");
    em.flush();
    em.clear();
    mvc.perform(get("/api/v1/links/me/favorites").header("Authorization", token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(2))
        .andExpect(jsonPath("$.hasMore").value(false))
        .andExpect(jsonPath("$.items[0].shortCode").value("work000"))
        .andExpect(jsonPath("$.items[0].note").value("오래 쓰는 링크"))
        .andExpect(jsonPath("$.items[0].timezone").value("Asia/Kathmandu"));
    mvc.perform(
            put("/api/v1/links/me/favorites/order")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"shortCodes\":[\"work084\",\"work000\"]}"))
        .andExpect(status().isNoContent());
    em.flush();
    em.clear();
    mvc.perform(get("/api/v1/links/me/favorites").header("Authorization", token))
        .andExpect(jsonPath("$.items[0].shortCode").value("work084"));
    mvc.perform(delete("/api/v1/links/work084").header("Authorization", token))
        .andExpect(status().isNoContent());
    mvc.perform(get("/api/v1/links/me/favorites").header("Authorization", token))
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].shortCode").value("work000"));
  }

  @Test
  void ownerChecksAndExactPermutationPreventForeignOrLostFavorites() throws Exception {
    makeLinks(2);
    links.save(new LinkEntity("https://example.org/private", "private1", other.getId(), null));
    putFavorite("work000");
    putFavorite("work001");
    mvc.perform(put("/api/v1/links/me/favorites/private1").header("Authorization", token))
        .andExpect(status().isForbidden());
    for (String order : List.of("[]", "[\"work000\",\"work000\"]", "[\"work000\",\"private1\"]")) {
      mvc.perform(
              put("/api/v1/links/me/favorites/order")
                  .header("Authorization", token)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"shortCodes\":" + order + "}"))
          .andExpect(status().isBadRequest());
    }
    mvc.perform(delete("/api/v1/links/me/favorites/private1").header("Authorization", token))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/links/me/favorites").header("Authorization", token))
        .andExpect(jsonPath("$.items.length()").value(2));
  }

  @Test
  void resolvesOwnedCodesInRequestedOrderWithoutPaginationOrForeignMetadata() throws Exception {
    makeLinks(85);
    links.save(new LinkEntity("https://example.org/private", "private1", other.getId(), null));
    mvc.perform(
            get("/api/v1/links/me/by-codes")
                .header("Authorization", token)
                .param("codes", "work084,private1,work000,deleted1,work084"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(2))
        .andExpect(jsonPath("$.items[0].shortCode").value("work084"))
        .andExpect(jsonPath("$.items[1].shortCode").value("work000"))
        .andExpect(jsonPath("$.hasMore").value(false));
    mvc.perform(
            get("/api/v1/links/me/by-codes")
                .header("Authorization", token)
                .param("codes", "work000", "work084"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(2));
  }

  @Test
  void overviewCountsAllLinksAndSharesExactAccountDayBucketsWithSelectedLink() throws Exception {
    List<LinkEntity> many = makeLinks(85);
    LinkEntity first = many.getFirst();
    first.changeExpiresAt(NOW.plusSeconds(3 * 86400));
    many.get(1).changeExpiresAt(NOW);
    many.get(2).changeExpiresAt(NOW.plusSeconds(3 * 86400 + 1));
    many.get(3).changeExpiresAt(NOW.minusSeconds(1));
    ZoneId zone = ZoneId.of(owner.getTimezone());
    LocalDate today = NOW.atZone(zone).toLocalDate();
    Instant weekStart = today.minusDays(6).atStartOfDay(zone).toInstant();
    Instant todayStart = today.atStartOfDay(zone).toInstant();
    click(first, weekStart.minusSeconds(1), false);
    click(first, weekStart, false);
    click(first, todayStart.minusSeconds(1), false);
    click(first, todayStart, false);
    click(first, NOW.minusSeconds(60), false);
    click(first, NOW.minusSeconds(30), true);
    click(many.getLast(), weekStart.plusSeconds(86400 * 3), false);
    // A bot-heavy link must not displace the strongest human-performing link.
    for (int i = 0; i < 20; i++) click(many.get(2), NOW.minusSeconds(120 + i), true);
    LinkEntity foreign =
        links.save(new LinkEntity("https://other.org", "foreign1", other.getId(), null));
    click(foreign, todayStart, false);
    em.flush();
    String body =
        mvc.perform(get("/api/v1/links/me/overview").header("Authorization", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalLinks").value(85))
            .andExpect(jsonPath("$.totalClicks").value(27))
            .andExpect(jsonPath("$.humanClicks").value(6))
            .andExpect(jsonPath("$.clicks7d").value(5))
            .andExpect(jsonPath("$.clicksToday").value(2))
            .andExpect(jsonPath("$.zeroClickLinks").value(82))
            .andExpect(jsonPath("$.expiringLinks").value(2))
            .andExpect(jsonPath("$.timezone").value("Asia/Kathmandu"))
            .andExpect(jsonPath("$.dailyClicks[0].date").value("2026-09-07"))
            .andExpect(jsonPath("$.dailyClicks[6].date").value("2026-09-13"))
            .andExpect(jsonPath("$.topLinks[0].shortCode").value("work000"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    List<Integer> daily = JsonPath.read(body, "$.dailyClicks[*].count");
    assertThat(daily).containsExactly(1, 0, 0, 1, 0, 1, 2);
    assertThat(daily.stream().mapToInt(Integer::intValue).sum()).isEqualTo(5);
    mvc.perform(
            get("/api/v1/links/me/by-codes")
                .header("Authorization", token)
                .param("codes", "work000"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].clickCount").value(6))
        .andExpect(jsonPath("$.items[0].humanClickCount").value(5))
        .andExpect(jsonPath("$.items[0].clicksLast7d[0]").value(1))
        .andExpect(jsonPath("$.items[0].clicksLast7d[5]").value(1))
        .andExpect(jsonPath("$.items[0].clicksLast7d[6]").value(2));
  }

  @Test
  void emptyOverviewIsSevenDatedZerosAndPrivateEndpointsRequireAuthentication() throws Exception {
    mvc.perform(get("/api/v1/links/me/overview").header("Authorization", token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalLinks").value(0))
        .andExpect(jsonPath("$.dailyClicks.length()").value(7))
        .andExpect(jsonPath("$.clicksToday").value(0))
        .andExpect(jsonPath("$.topLinks.length()").value(0));
    mvc.perform(get("/api/v1/links/me/overview")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/links/me/favorites")).andExpect(status().isUnauthorized());
  }

  @Test
  void clearingExpiryIsExplicitAndUnrelatedEditsKeepTheExistingDate() throws Exception {
    LinkEntity link = makeLinks(1).getFirst();
    Instant expiry = NOW.plusSeconds(604800);
    link.changeExpiresAt(expiry);
    mvc.perform(
            patch("/api/v1/links/work000")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"note\":\"캠페인 이름\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.expiresAt").value(expiry.toString()));
    mvc.perform(get("/api/v1/links/me").header("Authorization", token).param("q", "캠페인 이름"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1));
    mvc.perform(
            patch("/api/v1/links/work000")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expiresAt\":null}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.expiresAt").value(expiry.toString()));
    mvc.perform(
            patch("/api/v1/links/work000")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"clearExpiresAt\":true,\"expiresAt\":\"2027-01-01T00:00:00Z\"}"))
        .andExpect(status().isBadRequest());
    mvc.perform(
            patch("/api/v1/links/work000")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"clearExpiresAt\":true}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.expiresAt").doesNotExist());
    em.flush();
    em.clear();
    assertThat(
            links
                .findByShortCode(new com.example.short_link.link.domain.ShortCode("work000"))
                .orElseThrow()
                .getExpiresAt())
        .isNull();
  }

  private List<LinkEntity> makeLinks(int count) {
    List<LinkEntity> result = new ArrayList<>();
    for (int i = 0; i < count; i++)
      result.add(
          links.save(
              new LinkEntity(
                  "https://example.com/" + i, String.format("work%03d", i), owner.getId(), null)));
    return result;
  }

  private void putFavorite(String code) throws Exception {
    mvc.perform(put("/api/v1/links/me/favorites/" + code).header("Authorization", token))
        .andExpect(status().isNoContent());
  }

  private void click(LinkEntity link, Instant at, boolean bot) {
    clicks.save(ClickEventEntity.builder().linkId(link.linkId()).clickedAt(at).bot(bot).build());
  }
}
