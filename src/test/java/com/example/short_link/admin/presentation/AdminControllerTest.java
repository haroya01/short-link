package com.example.short_link.admin.presentation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.common.observability.RequestMetric;
import com.example.short_link.common.observability.RequestMetricEntity;
import com.example.short_link.common.observability.RequestMetricRepository;
import com.example.short_link.link.domain.ClickEventEntity;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.repository.ClickEventRepository;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.user.application.JwtTokenService;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminControllerTest {

  @Autowired private MockMvc mvc;
  @Autowired private JwtTokenService jwt;
  @Autowired private UserRepository userRepository;
  @Autowired private LinkRepository linkRepository;
  @Autowired private ClickEventRepository clickRepository;
  @Autowired private RequestMetricRepository requestMetricRepository;

  @Test
  void anonymousReceives401OnAdminEndpoint() throws Exception {
    mvc.perform(get("/api/v1/admin/overview")).andExpect(status().isUnauthorized());
  }

  @Test
  void plainUserReceives403OnAdminEndpoint() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("user@x.com", "google", "g-u"));
    String token = jwt.createAccessToken(user.getId(), "USER");

    mvc.perform(get("/api/v1/admin/overview").header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden());
  }

  @Test
  void anonymousReceives401OnHealthMetrics() throws Exception {
    mvc.perform(get("/api/v1/admin/health-metrics")).andExpect(status().isUnauthorized());
  }

  @Test
  void anonymousReceives401OnRouteMetrics() throws Exception {
    mvc.perform(get("/api/v1/admin/route-metrics")).andExpect(status().isUnauthorized());
  }

  @Test
  void plainUserReceives403OnRouteMetrics() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("u@x.com", "google", "g-urm"));
    String token = jwt.createAccessToken(user.getId(), "USER");
    mvc.perform(get("/api/v1/admin/route-metrics").header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden());
  }

  @Test
  void adminReceivesRouteMetrics() throws Exception {
    UserEntity admin = userRepository.save(new UserEntity("rm@x.com", "google", "g-arm"));
    admin.promoteToAdmin();
    userRepository.save(admin);
    String token = jwt.createAccessToken(admin.getId(), "ADMIN");

    mvc.perform(get("/api/v1/admin/route-metrics").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray());
  }

  @Test
  void anonymousReceives401OnLinkMetrics() throws Exception {
    mvc.perform(get("/api/v1/admin/link-metrics")).andExpect(status().isUnauthorized());
  }

  @Test
  void plainUserReceives403OnLinkMetrics() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("u@x.com", "google", "g-ulm"));
    String token = jwt.createAccessToken(user.getId(), "USER");
    mvc.perform(get("/api/v1/admin/link-metrics").header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden());
  }

  @Test
  void adminReceivesLinkMetrics() throws Exception {
    UserEntity admin = userRepository.save(new UserEntity("lm@x.com", "google", "g-alm"));
    admin.promoteToAdmin();
    userRepository.save(admin);
    LinkEntity link =
        linkRepository.save(new LinkEntity("https://example.com", "lmcode", admin.getId(), null));
    // Seed the click event for the lifetime totals + the two request_metrics rows the windowed
    // outcome breakdown reads from.
    clickRepository.save(
        ClickEventEntity.builder()
            .linkId(link.getId())
            .userAgent("ua")
            .clientIp("1.1.1.1")
            .deviceClass("desktop")
            .bot(false)
            .build());
    requestMetricRepository.save(
        new RequestMetricEntity(
            new RequestMetric(
                Instant.now(),
                "/r/{shortCode}",
                "GET",
                302,
                "redirect",
                15,
                "lmcode",
                null,
                null)));
    requestMetricRepository.save(
        new RequestMetricEntity(
            new RequestMetric(
                Instant.now(),
                "/r/{shortCode}",
                "GET",
                404,
                "not_found",
                200,
                "lmcode",
                null,
                null)));
    String token = jwt.createAccessToken(admin.getId(), "ADMIN");

    mvc.perform(
            get("/api/v1/admin/link-metrics")
                .header("Authorization", "Bearer " + token)
                .param("window", "24h")
                .param("sort", "count"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        // The recorder is a singleton across tests in the shared application context, so other
        // suites may have seeded codes too — look up our row by shortCode rather than asserting
        // on the first element.
        .andExpect(
            jsonPath("$[?(@.shortCode == 'lmcode')].originalUrl").value("https://example.com"))
        .andExpect(jsonPath("$[?(@.shortCode == 'lmcode')].windowedRedirects").value(2))
        .andExpect(jsonPath("$[?(@.shortCode == 'lmcode')].outcomeCounts.redirect").value(1))
        .andExpect(jsonPath("$[?(@.shortCode == 'lmcode')].outcomeCounts.not_found").value(1));
  }

  @Test
  void anonymousReceives401OnRecentErrors() throws Exception {
    mvc.perform(get("/api/v1/admin/recent-errors")).andExpect(status().isUnauthorized());
  }

  @Test
  void plainUserReceives403OnHealthMetrics() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("u@x.com", "google", "g-uh"));
    String token = jwt.createAccessToken(user.getId(), "USER");
    mvc.perform(get("/api/v1/admin/health-metrics").header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden());
  }

  @Test
  void adminReceivesHealthMetrics() throws Exception {
    UserEntity admin = userRepository.save(new UserEntity("h@x.com", "google", "g-ah"));
    admin.promoteToAdmin();
    userRepository.save(admin);
    String token = jwt.createAccessToken(admin.getId(), "ADMIN");

    mvc.perform(get("/api/v1/admin/health-metrics").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.httpLatency").isMap())
        .andExpect(jsonPath("$.httpStatusCounts").isMap())
        .andExpect(jsonPath("$.rateLimitExceeded").isNumber())
        .andExpect(jsonPath("$.safeBrowsingMalicious").isNumber())
        .andExpect(jsonPath("$.authFailures").isNumber())
        .andExpect(jsonPath("$.dbPool").isMap())
        .andExpect(jsonPath("$.cache").isMap());
  }

  @Test
  void adminReceivesRecentErrors() throws Exception {
    UserEntity admin = userRepository.save(new UserEntity("e@x.com", "google", "g-ae"));
    admin.promoteToAdmin();
    userRepository.save(admin);
    String token = jwt.createAccessToken(admin.getId(), "ADMIN");

    mvc.perform(get("/api/v1/admin/recent-errors").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray());
  }

  @Test
  void adminReceivesCohort() throws Exception {
    UserEntity admin = userRepository.save(new UserEntity("c@x.com", "google", "g-cohort"));
    admin.promoteToAdmin();
    userRepository.save(admin);
    String token = jwt.createAccessToken(admin.getId(), "ADMIN");

    mvc.perform(
            get("/api/v1/admin/cohort")
                .header("Authorization", "Bearer " + token)
                .param("weeks", "4"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.weeks").value(4))
        .andExpect(jsonPath("$.rows").isArray());
  }

  @Test
  void adminReceivesLifecycle() throws Exception {
    UserEntity admin = userRepository.save(new UserEntity("l@x.com", "google", "g-life"));
    admin.promoteToAdmin();
    userRepository.save(admin);
    String token = jwt.createAccessToken(admin.getId(), "ADMIN");

    mvc.perform(
            get("/api/v1/admin/lifecycle")
                .header("Authorization", "Bearer " + token)
                .param("days", "14"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.maxDay").value(14))
        .andExpect(jsonPath("$.days").isArray());
  }

  @Test
  void adminReceivesActiveUsers() throws Exception {
    UserEntity admin = userRepository.save(new UserEntity("a@x.com", "google", "g-active"));
    admin.promoteToAdmin();
    userRepository.save(admin);
    String token = jwt.createAccessToken(admin.getId(), "ADMIN");

    mvc.perform(
            get("/api/v1/admin/active-users")
                .header("Authorization", "Bearer " + token)
                .param("period", "week"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.period").value("week"))
        .andExpect(jsonPath("$.buckets").isArray());
  }

  @Test
  void rejectsInvalidActivePeriod() throws Exception {
    UserEntity admin = userRepository.save(new UserEntity("p@x.com", "google", "g-pinval"));
    admin.promoteToAdmin();
    userRepository.save(admin);
    String token = jwt.createAccessToken(admin.getId(), "ADMIN");

    mvc.perform(
            get("/api/v1/admin/active-users")
                .header("Authorization", "Bearer " + token)
                .param("period", "weird"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_ACTIVE_PERIOD"));
  }

  @Test
  void plainUserReceives403OnAnalyticsEndpoints() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("u@x.com", "google", "g-up"));
    String token = jwt.createAccessToken(user.getId(), "USER");

    mvc.perform(get("/api/v1/admin/cohort").header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/admin/lifecycle").header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/admin/active-users").header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden());
  }

  @Test
  void adminReceivesOverviewWithKpis() throws Exception {
    UserEntity admin = userRepository.save(new UserEntity("admin@x.com", "google", "g-a"));
    admin.promoteToAdmin();
    userRepository.save(admin);

    LinkEntity link =
        linkRepository.save(new LinkEntity("https://example.com", "adov001", admin.getId(), null));
    clickRepository.save(
        ClickEventEntity.builder()
            .linkId(link.getId())
            .userAgent("ua")
            .clientIp("1.1.1.1")
            .deviceClass("desktop")
            .bot(false)
            .build());
    String token = jwt.createAccessToken(admin.getId(), "ADMIN");

    mvc.perform(get("/api/v1/admin/overview").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totals.users").isNumber())
        .andExpect(jsonPath("$.totals.links").isNumber())
        .andExpect(jsonPath("$.totals.clicks").isNumber())
        .andExpect(jsonPath("$.dailySignups").isArray())
        .andExpect(jsonPath("$.dailyLinks").isArray())
        .andExpect(jsonPath("$.dailyClicks").isArray())
        .andExpect(jsonPath("$.topUsersByLinks").isArray())
        .andExpect(jsonPath("$.topUsersByLinksTotal").isNumber())
        .andExpect(jsonPath("$.topUsersByClicks").isArray())
        .andExpect(jsonPath("$.topUsersByClicksTotal").isNumber())
        .andExpect(jsonPath("$.topLinksByClicks").isArray())
        .andExpect(jsonPath("$.topLinksByClicksTotal").isNumber());
  }

  @Test
  void anonymousReceives401OnTopPages() throws Exception {
    mvc.perform(get("/api/v1/admin/top-users-by-links")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/admin/top-users-by-clicks")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/admin/top-links-by-clicks")).andExpect(status().isUnauthorized());
  }

  @Test
  void plainUserReceives403OnTopPages() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("u@x.com", "google", "g-utp"));
    String token = jwt.createAccessToken(user.getId(), "USER");
    mvc.perform(get("/api/v1/admin/top-users-by-links").header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/admin/top-users-by-clicks").header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/admin/top-links-by-clicks").header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden());
  }

  @Test
  void adminReceivesTopPagesShape() throws Exception {
    UserEntity admin = userRepository.save(new UserEntity("tp@x.com", "google", "g-atp"));
    admin.promoteToAdmin();
    userRepository.save(admin);
    String token = jwt.createAccessToken(admin.getId(), "ADMIN");

    // Controller test asserts shape + caps; ordering / totalElements correctness is covered at
    // the repository slice level (AdminMetricsRepositoryTopPageTest) since the shared test DB
    // carries pre-existing seed data that makes deterministic ordering assertions brittle here.
    mvc.perform(
            get("/api/v1/admin/top-users-by-links?page=0&size=10")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items").isArray())
        .andExpect(jsonPath("$.total").isNumber());

    mvc.perform(
            get("/api/v1/admin/top-users-by-clicks?page=0&size=10")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items").isArray())
        .andExpect(jsonPath("$.total").isNumber());

    mvc.perform(
            get("/api/v1/admin/top-links-by-clicks?page=0&size=10000")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items").isArray())
        .andExpect(jsonPath("$.total").isNumber());
  }
}
