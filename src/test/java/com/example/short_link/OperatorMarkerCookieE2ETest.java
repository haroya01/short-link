package com.example.short_link;

import static com.example.short_link.support.TestCacheCleaner.clear;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.common.security.OperatorMarkerStore;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.stats.application.ClickBuffer;
import com.example.short_link.link.stats.application.ClickFlusher;
import com.example.short_link.link.stats.application.read.ClickTotalsReader;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Tag("integration")
class OperatorMarkerCookieE2ETest {

  private static final String DESKTOP_SAFARI =
      "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko)"
          + " Version/17.4 Safari/605.1.15";
  private static final int WEB_REFRESH_TTL_SECONDS = 14 * 24 * 60 * 60;

  private record Session(String accessToken, String refreshToken, String marker) {}

  @Autowired private MockMvc mvc;
  @Autowired private UserRepository users;
  @Autowired private LinkRepository links;
  @Autowired private ClickBuffer clickBuffer;
  @Autowired private ClickFlusher clickFlusher;
  @Autowired private ClickTotalsReader totals;
  @Autowired private OperatorMarkerStore markers;
  @Autowired private CacheManager cacheManager;

  private final List<String> seenMarkers = new ArrayList<>();
  private final String clientIp =
      "198.18."
          + ThreadLocalRandom.current().nextInt(256)
          + "."
          + ThreadLocalRandom.current().nextInt(1, 255);
  private String suffix;

  @BeforeEach
  void aCleanSlate() {
    clickBuffer.drain(Integer.MAX_VALUE);
    clear(cacheManager, "link");
    suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
  }

  @AfterEach
  void forgetMarkers() {
    seenMarkers.forEach(markers::revoke);
    clickBuffer.drain(Integer.MAX_VALUE);
  }

  @Test
  void anAdminLoginSetsTheMarkerCookieForTheWholeSite() throws Exception {
    String email = anAdmin();

    ResultActions login = devLogin(email, null);

    login
        .andExpect(status().isOk())
        .andExpect(cookie().exists("refresh_token"))
        .andExpect(cookie().path("kurl_op", "/"))
        .andExpect(cookie().maxAge("kurl_op", WEB_REFRESH_TTL_SECONDS))
        .andExpect(cookie().httpOnly("kurl_op", true))
        .andExpect(cookie().secure("kurl_op", true))
        .andExpect(cookie().sameSite("kurl_op", "Lax"));
    String marker = remember(sessionFrom(login).marker());
    assertThat(marker).matches("[A-Za-z0-9_-]{32}");
    assertThat(markers.isValid(marker)).isTrue();
  }

  @Test
  void refreshKeepsTheSameMarkerAndRenewsTheCookie() throws Exception {
    Session login = sessionFrom(devLogin(anAdmin(), null));
    remember(login.marker());

    ResultActions first = refresh(login.refreshToken(), login.marker());
    first
        .andExpect(status().isOk())
        .andExpect(cookie().value("kurl_op", login.marker()))
        .andExpect(cookie().maxAge("kurl_op", WEB_REFRESH_TTL_SECONDS))
        .andExpect(cookie().sameSite("kurl_op", "Lax"));
    ResultActions second = refresh(sessionFrom(first).refreshToken(), login.marker());

    second.andExpect(status().isOk()).andExpect(cookie().value("kurl_op", login.marker()));
    assertThat(markers.isValid(login.marker())).isTrue();
  }

  @Test
  void anAdminRefreshFromABrowserWithoutTheMarkerMintsOne() throws Exception {
    Session login = sessionFrom(devLogin(anAdmin(), null));
    remember(login.marker());

    ResultActions refreshed = refresh(login.refreshToken(), null);

    refreshed.andExpect(status().isOk()).andExpect(cookie().exists("kurl_op"));
    String minted = remember(sessionFrom(refreshed).marker());
    assertThat(minted).isNotEqualTo(login.marker());
    assertThat(markers.isValid(minted)).isTrue();
  }

  @Test
  void theMarkerFromLoginKeepsThatBrowsersVisitsOutOfTheHumanCountUntilLogout() throws Exception {
    Session login = sessionFrom(devLogin(anAdmin(), null));
    remember(login.marker());
    LinkEntity link = links.save(new LinkEntity("https://example.com/landing", "op" + suffix));

    visit(link, login.marker()).andExpect(status().isFound());
    clickFlusher.flush();
    assertThat(totals.countHumanByLinkId(link.getId())).isZero();
    assertThat(totals.countBotByLinkId(link.getId())).isEqualTo(1);

    mvc.perform(
            fromThisBrowser(
                post("/api/v1/auth/logout")
                    .header("Authorization", "Bearer " + login.accessToken())
                    .cookie(new Cookie("refresh_token", login.refreshToken()))
                    .cookie(new Cookie("kurl_op", login.marker()))))
        .andExpect(status().isOk())
        .andExpect(cookie().value("kurl_op", ""))
        .andExpect(cookie().maxAge("kurl_op", 0))
        .andExpect(cookie().path("kurl_op", "/"));
    assertThat(markers.isValid(login.marker())).isFalse();

    visit(link, login.marker()).andExpect(status().isFound());
    clickFlusher.flush();
    assertThat(totals.countHumanByLinkId(link.getId())).isEqualTo(1);
    assertThat(totals.countBotByLinkId(link.getId())).isEqualTo(1);
  }

  @Test
  void aMemberGetsNoMarkerOnLoginOrRefresh() throws Exception {
    ResultActions login = devLogin(aMember(), null);

    login.andExpect(status().isOk()).andExpect(cookie().doesNotExist("kurl_op"));
    refresh(sessionFrom(login).refreshToken(), null)
        .andExpect(status().isOk())
        .andExpect(cookie().doesNotExist("kurl_op"));
  }

  @Test
  void aMemberSigningInWhereAnAdminLeftAMarkerHasItRevokedAndCleared() throws Exception {
    String left = remember(markers.issue(Duration.ofMinutes(5)));

    ResultActions login = devLogin(aMember(), left);

    login
        .andExpect(status().isOk())
        .andExpect(cookie().value("kurl_op", ""))
        .andExpect(cookie().maxAge("kurl_op", 0));
    assertThat(markers.isValid(left)).isFalse();
  }

  @Test
  void aMemberRefreshCarryingAMarkerHasItRevokedAndCleared() throws Exception {
    Session member = sessionFrom(devLogin(aMember(), null));
    String carried = remember(markers.issue(Duration.ofMinutes(5)));

    refresh(member.refreshToken(), carried)
        .andExpect(status().isOk())
        .andExpect(cookie().value("kurl_op", ""))
        .andExpect(cookie().maxAge("kurl_op", 0));
    assertThat(markers.isValid(carried)).isFalse();
  }

  private String anAdmin() {
    String email = "admin-" + suffix + "@example.com";
    UserEntity admin = new UserEntity(email, "dev", "dev:" + email);
    admin.promoteToAdmin();
    users.save(admin);
    return email;
  }

  private String aMember() {
    return "member-" + suffix + "@example.com";
  }

  private String remember(String marker) {
    seenMarkers.add(marker);
    return marker;
  }

  private ResultActions devLogin(String email, String carriedMarker) throws Exception {
    MockHttpServletRequestBuilder request =
        post("/api/v1/auth/dev-login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"" + email + "\"}");
    if (carriedMarker != null) {
      request.cookie(new Cookie("kurl_op", carriedMarker));
    }
    return mvc.perform(fromThisBrowser(request));
  }

  private ResultActions refresh(String refreshToken, String carriedMarker) throws Exception {
    MockHttpServletRequestBuilder request =
        post("/api/v1/auth/refresh").cookie(new Cookie("refresh_token", refreshToken));
    if (carriedMarker != null) {
      request.cookie(new Cookie("kurl_op", carriedMarker));
    }
    return mvc.perform(fromThisBrowser(request));
  }

  private ResultActions visit(LinkEntity link, String marker) throws Exception {
    return mvc.perform(
        fromThisBrowser(
            get("/" + link.getShortCode().value())
                .header("User-Agent", DESKTOP_SAFARI)
                .cookie(new Cookie("kurl_op", marker))));
  }

  private MockHttpServletRequestBuilder fromThisBrowser(MockHttpServletRequestBuilder request) {
    return request.with(
        servletRequest -> {
          servletRequest.setRemoteAddr(clientIp);
          return servletRequest;
        });
  }

  private static Session sessionFrom(ResultActions result) throws Exception {
    MockHttpServletResponse response = result.andReturn().getResponse();
    Cookie marker = response.getCookie("kurl_op");
    return new Session(
        JsonPath.read(response.getContentAsString(), "$.accessToken"),
        response.getCookie("refresh_token").getValue(),
        marker == null ? null : marker.getValue());
  }
}
