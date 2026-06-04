package com.example.short_link.post.presentation;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.post.application.read.AuthorAnalyticsOverview;
import com.example.short_link.post.application.read.DailyPoint;
import com.example.short_link.post.application.read.PostAnalyticsQueryService;
import com.example.short_link.post.application.read.PostAnalyticsView;
import com.example.short_link.post.application.read.PostReadStats;
import com.example.short_link.post.application.read.PostReadStatsService;
import com.example.short_link.post.application.read.TopPostView;
import com.example.short_link.post.domain.PostLinkClick;
import com.example.short_link.post.exception.PostErrorCode;
import com.example.short_link.post.exception.PostException;
import com.example.short_link.testsupport.KurlWebMvcTest;
import com.example.short_link.testsupport.WebMvcSecurityTestConfig;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@KurlWebMvcTest(controllers = PostAnalyticsController.class)
@Import(PostExceptionHandler.class)
class PostAnalyticsControllerTest {

  @Autowired private MockMvc mvc;

  @MockitoBean private PostAnalyticsQueryService analytics;
  @MockitoBean private PostReadStatsService readStats;

  private static final long USER_ID = 7L;

  @Test
  void anonymousOverviewIs401() throws Exception {
    mvc.perform(get("/api/v1/posts/analytics/overview")).andExpect(status().isUnauthorized());
  }

  @Test
  void overviewReturnsTotals() throws Exception {
    when(analytics.overview(eq(USER_ID), anyInt()))
        .thenReturn(
            new AuthorAnalyticsOverview(
                3,
                2,
                155,
                14,
                30,
                9,
                40,
                6,
                8,
                2,
                List.of(new DailyPoint(LocalDate.parse("2026-06-01"), 9)),
                List.of(new TopPostView(1L, "a", "A", 100, 10, 7))));

    mvc.perform(
            get("/api/v1/posts/analytics/overview?days=30")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalPosts").value(3))
        .andExpect(jsonPath("$.publishedPosts").value(2))
        .andExpect(jsonPath("$.lifetimeViews").value(155))
        .andExpect(jsonPath("$.lifetimeLinkClicks").value(40))
        .andExpect(jsonPath("$.lifetimeFollows").value(8))
        .andExpect(jsonPath("$.windowFollows").value(2))
        .andExpect(jsonPath("$.topPosts[0].slug").value("a"))
        .andExpect(jsonPath("$.topPosts[0].followsGained").value(7));
  }

  @Test
  void postAnalyticsReturnsView() throws Exception {
    when(analytics.postAnalytics(eq(USER_ID), eq(1L), anyInt()))
        .thenReturn(
            new PostAnalyticsView(
                1L,
                "hello",
                "Hello",
                "PUBLISHED",
                12,
                4,
                7,
                7,
                30,
                9,
                5,
                2,
                List.of(new DailyPoint(LocalDate.parse("2026-06-01"), 5)),
                List.of(new PostLinkClick("abc123", "https://example.com", 4))));

    mvc.perform(
            get("/api/v1/posts/1/analytics?days=7")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.slug").value("hello"))
        .andExpect(jsonPath("$.lifetimeViews").value(12))
        .andExpect(jsonPath("$.windowViews").value(7))
        .andExpect(jsonPath("$.lifetimeLinkClicks").value(30))
        .andExpect(jsonPath("$.windowLinkClicks").value(9))
        .andExpect(jsonPath("$.lifetimeFollows").value(5))
        .andExpect(jsonPath("$.windowFollows").value(2))
        .andExpect(jsonPath("$.daily[0].views").value(5))
        .andExpect(jsonPath("$.linkBreakdown[0].shortCode").value("abc123"))
        .andExpect(jsonPath("$.linkBreakdown[0].clicks").value(4));
  }

  @Test
  void postStatsReturnsReaderBreakdown() throws Exception {
    when(readStats.forPost(eq(USER_ID), eq(1L)))
        .thenReturn(
            new PostReadStats(
                "Asia/Seoul",
                120,
                100,
                20,
                80,
                null,
                null,
                21,
                List.of(),
                List.of(),
                List.of(),
                List.of(new PostReadStats.CountryVisit("KR", 80)),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of()));

    mvc.perform(
            get("/api/v1/posts/1/stats").header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalVisits").value(120))
        .andExpect(jsonPath("$.humanVisits").value(100))
        .andExpect(jsonPath("$.countryVisits[0].country").value("KR"));
  }

  @Test
  void postAnalyticsForOtherOwnerIs403() throws Exception {
    when(analytics.postAnalytics(eq(USER_ID), eq(2L), anyInt()))
        .thenThrow(new PostException(PostErrorCode.PERMISSION_DENIED));

    mvc.perform(
            get("/api/v1/posts/2/analytics")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
  }
}
