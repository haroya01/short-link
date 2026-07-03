package com.example.short_link.admin.presentation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.common.observability.RequestMetric;
import com.example.short_link.common.observability.RequestMetricEntity;
import com.example.short_link.common.observability.RequestMetricJpaRepository;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.stats.domain.ClickEventEntity;
import com.example.short_link.link.stats.domain.repository.ClickEventRepository;
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
  @Autowired private RequestMetricJpaRepository requestMetricRepository;

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
  void adminMintsAccessToken() throws Exception {
    UserEntity admin = userRepository.save(new UserEntity("mint@x.com", "google", "g-mint"));
    admin.promoteToAdmin();
    userRepository.save(admin);
    String token = jwt.createAccessToken(admin.getId(), "ADMIN");

    mvc.perform(post("/api/v1/admin/access-token").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").isString())
        .andExpect(jsonPath("$.expiresInSeconds").isNumber());
  }

  @Test
  void plainUserCannotMintAccessToken() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("nomint@x.com", "google", "g-nomint"));
    String token = jwt.createAccessToken(user.getId(), "USER");

    mvc.perform(post("/api/v1/admin/access-token").header("Authorization", "Bearer " + token))
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
            .linkId(link.linkId())
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
            .linkId(link.linkId())
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

  // ─── Full-table browse (users / links) ───────────────────────────────────

  @Test
  void anonymousReceives401OnBrowse() throws Exception {
    mvc.perform(get("/api/v1/admin/users")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/admin/users/1")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/admin/links")).andExpect(status().isUnauthorized());
  }

  @Test
  void plainUserReceives403OnBrowse() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("u@x.com", "google", "g-ubrowse"));
    String token = jwt.createAccessToken(user.getId(), "USER");
    mvc.perform(get("/api/v1/admin/users").header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/admin/users/1").header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/admin/links").header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden());
  }

  @Test
  void adminBrowsesUsersFiltersBySearchAndRole() throws Exception {
    UserEntity admin = userRepository.save(new UserEntity("browse-admin@x.com", "google", "g-bad"));
    admin.promoteToAdmin();
    userRepository.save(admin);
    // A plain user that owns one link — its email is the search term and its linkCount must be 1.
    UserEntity subject =
        userRepository.save(new UserEntity("subject-abc@x.com", "google", "g-sabc"));
    linkRepository.save(new LinkEntity("https://example.com/a", "brwuser1", subject.getId(), null));
    String token = jwt.createAccessToken(admin.getId(), "ADMIN");

    mvc.perform(
            get("/api/v1/admin/users?q=subject-abc&size=10")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").isNumber())
        .andExpect(jsonPath("$.items[?(@.email == 'subject-abc@x.com')].role").value("USER"))
        .andExpect(jsonPath("$.items[?(@.email == 'subject-abc@x.com')].linkCount").value(1))
        .andExpect(jsonPath("$.items[?(@.email == 'subject-abc@x.com')].deleted").value(false));

    // role=ADMIN must exclude the plain user.
    mvc.perform(
            get("/api/v1/admin/users?q=subject-abc&role=ADMIN")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[?(@.email == 'subject-abc@x.com')]").isEmpty());
  }

  @Test
  void adminBrowseUsersRejectsInvalidRole() throws Exception {
    String token = jwt.createAccessToken(adminId(), "ADMIN");
    mvc.perform(
            get("/api/v1/admin/users?role=superuser").header("Authorization", "Bearer " + token))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_ROLE"));
  }

  @Test
  void adminReadsUserDetailAnd404sWhenMissing() throws Exception {
    UserEntity admin = userRepository.save(new UserEntity("detail-admin@x.com", "google", "g-dad"));
    admin.promoteToAdmin();
    userRepository.save(admin);
    UserEntity subject = userRepository.save(new UserEntity("detail-me@x.com", "google", "g-dme"));
    String token = jwt.createAccessToken(admin.getId(), "ADMIN");

    mvc.perform(
            get("/api/v1/admin/users/" + subject.getId())
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value("detail-me@x.com"))
        .andExpect(jsonPath("$.role").value("USER"))
        .andExpect(jsonPath("$.linkCount").value(0));

    mvc.perform(get("/api/v1/admin/users/999999999").header("Authorization", "Bearer " + token))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
  }

  @Test
  void adminBrowsesLinksBySearchAndOwner() throws Exception {
    UserEntity admin = userRepository.save(new UserEntity("link-admin@x.com", "google", "g-lad"));
    admin.promoteToAdmin();
    userRepository.save(admin);
    LinkEntity link =
        linkRepository.save(
            new LinkEntity("https://browse.example.com/x", "brwlink1", admin.getId(), null));
    clickRepository.save(
        ClickEventEntity.builder()
            .linkId(link.linkId())
            .userAgent("ua")
            .clientIp("1.1.1.1")
            .deviceClass("desktop")
            .bot(false)
            .build());
    String token = jwt.createAccessToken(admin.getId(), "ADMIN");

    // Exact short-code match.
    mvc.perform(get("/api/v1/admin/links?q=brwlink1").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").isNumber())
        .andExpect(
            jsonPath("$.items[?(@.shortCode == 'brwlink1')].originalUrl")
                .value("https://browse.example.com/x"))
        .andExpect(jsonPath("$.items[?(@.shortCode == 'brwlink1')].clickCount").value(1))
        .andExpect(jsonPath("$.items[?(@.shortCode == 'brwlink1')].status").value("ACTIVE"))
        .andExpect(
            jsonPath("$.items[?(@.shortCode == 'brwlink1')].ownerEmail").value("link-admin@x.com"));

    // Destination-URL substring match.
    mvc.perform(
            get("/api/v1/admin/links?q=browse.example").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[?(@.shortCode == 'brwlink1')].shortCode").value("brwlink1"));

    // ownerId filter to an unrelated id must drop the link.
    mvc.perform(
            get("/api/v1/admin/links?q=brwlink1&ownerId=999999999")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[?(@.shortCode == 'brwlink1')]").isEmpty());
  }

  @Test
  void adminBrowseLinksDerivesExpiredStatus() throws Exception {
    UserEntity admin = userRepository.save(new UserEntity("exp-admin@x.com", "google", "g-ead"));
    admin.promoteToAdmin();
    userRepository.save(admin);
    linkRepository.save(
        new LinkEntity(
            "https://expired.example.com",
            "brwexp01",
            admin.getId(),
            Instant.now().minusSeconds(3600)));
    String token = jwt.createAccessToken(admin.getId(), "ADMIN");

    mvc.perform(get("/api/v1/admin/links?q=brwexp01").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[?(@.shortCode == 'brwexp01')].status").value("EXPIRED"));
  }

  @Test
  void anonymousReceives401OnLinkDetailAndActivity() throws Exception {
    mvc.perform(get("/api/v1/admin/links/anycode1")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/admin/links/activity")).andExpect(status().isUnauthorized());
  }

  @Test
  void plainUserReceives403OnLinkDetailAndActivity() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("u-ld@x.com", "google", "g-uld"));
    String token = jwt.createAccessToken(user.getId(), "USER");
    mvc.perform(get("/api/v1/admin/links/anycode1").header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/admin/links/activity").header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden());
  }

  @Test
  void adminSortsLinksByClicks() throws Exception {
    UserEntity admin =
        userRepository.save(new UserEntity("sort-admin@x.com", "google", "g-srtadm"));
    admin.promoteToAdmin();
    userRepository.save(admin);
    // Two links sharing a unique URL token so a q filter isolates exactly this pair; the "busy" one
    // gets two clicks, the "quiet" one none. sort=clicks must return busy first.
    LinkEntity busy =
        linkRepository.save(
            new LinkEntity("https://srtst.example.com/busy", "srtsbusy", admin.getId(), null));
    linkRepository.save(
        new LinkEntity("https://srtst.example.com/quiet", "srtsquiet", admin.getId(), null));
    for (int i = 0; i < 2; i++) {
      clickRepository.save(
          ClickEventEntity.builder()
              .linkId(busy.linkId())
              .userAgent("ua")
              .clientIp("1.1.1.1")
              .deviceClass("desktop")
              .bot(false)
              .build());
    }
    String token = jwt.createAccessToken(admin.getId(), "ADMIN");

    mvc.perform(
            get("/api/v1/admin/links?q=srtst.example&sort=clicks")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].shortCode").value("srtsbusy"))
        .andExpect(jsonPath("$.items[0].clickCount").value(2))
        .andExpect(jsonPath("$.items[1].shortCode").value("srtsquiet"))
        .andExpect(jsonPath("$.items[1].clickCount").value(0));
  }

  @Test
  void adminReadsLinkDetailWithStatsAnd404sWhenMissing() throws Exception {
    UserEntity admin = userRepository.save(new UserEntity("dtl-admin@x.com", "google", "g-dtladm"));
    admin.promoteToAdmin();
    userRepository.save(admin);
    LinkEntity link =
        linkRepository.save(
            new LinkEntity("https://detail.example.com", "dtlcode1", admin.getId(), null));
    clickRepository.save(
        ClickEventEntity.builder()
            .linkId(link.linkId())
            .userAgent("ua")
            .clientIp("1.1.1.1")
            .deviceClass("mobile")
            .bot(false)
            .build());
    String token = jwt.createAccessToken(admin.getId(), "ADMIN");

    mvc.perform(get("/api/v1/admin/links/dtlcode1").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.meta.shortCode").value("dtlcode1"))
        .andExpect(jsonPath("$.meta.originalUrl").value("https://detail.example.com"))
        .andExpect(jsonPath("$.meta.ownerEmail").value("dtl-admin@x.com"))
        .andExpect(jsonPath("$.meta.clickCount").value(1))
        .andExpect(jsonPath("$.meta.status").value("ACTIVE"))
        .andExpect(jsonPath("$.stats.shortCode").value("dtlcode1"))
        .andExpect(jsonPath("$.stats.totalClicks").isNumber())
        .andExpect(jsonPath("$.stats.dailyClicks").isArray())
        .andExpect(jsonPath("$.stats.deviceClicks").isArray());

    // Well-formed but nonexistent code → 404 with the admin-namespaced code.
    mvc.perform(get("/api/v1/admin/links/nosuch99").header("Authorization", "Bearer " + token))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("LINK_NOT_FOUND"));
  }

  @Test
  void adminReceivesLinkActivityFeed() throws Exception {
    UserEntity admin =
        userRepository.save(new UserEntity("actv-admin@x.com", "google", "g-actadm"));
    admin.promoteToAdmin();
    userRepository.save(admin);
    LinkEntity link =
        linkRepository.save(
            new LinkEntity("https://activity.example.com", "actvcod1", admin.getId(), null));
    clickRepository.save(
        ClickEventEntity.builder()
            .linkId(link.linkId())
            .userAgent("ua")
            .clientIp("1.1.1.1")
            .deviceClass("desktop")
            .bot(false)
            .build());
    String token = jwt.createAccessToken(admin.getId(), "ADMIN");

    mvc.perform(get("/api/v1/admin/links/activity").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.recentLinks").isArray())
        .andExpect(jsonPath("$.recentClicks").isArray())
        .andExpect(jsonPath("$.trending24h").isArray())
        .andExpect(jsonPath("$.recentLinks[0].shortCode").isString())
        .andExpect(jsonPath("$.recentClicks[0].shortCode").isString())
        // Click rows are PII-minimal — IP and visitor hash are never serialized.
        .andExpect(jsonPath("$.recentClicks[0].clientIp").doesNotExist())
        .andExpect(jsonPath("$.recentClicks[0].visitorHash").doesNotExist());
  }

  private Long adminId() {
    UserEntity admin = userRepository.save(new UserEntity("gen-admin@x.com", "google", "g-gad"));
    admin.promoteToAdmin();
    return userRepository.save(admin).getId();
  }
}
