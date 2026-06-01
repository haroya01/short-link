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
import com.example.short_link.post.application.read.TopPostView;
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
                List.of(new DailyPoint(LocalDate.parse("2026-06-01"), 9)),
                List.of(new TopPostView(1L, "a", "A", 100, 10))));

    mvc.perform(
            get("/api/v1/posts/analytics/overview?days=30")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalPosts").value(3))
        .andExpect(jsonPath("$.publishedPosts").value(2))
        .andExpect(jsonPath("$.lifetimeViews").value(155))
        .andExpect(jsonPath("$.topPosts[0].slug").value("a"));
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
                List.of(new DailyPoint(LocalDate.parse("2026-06-01"), 5))));

    mvc.perform(
            get("/api/v1/posts/1/analytics?days=7")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.slug").value("hello"))
        .andExpect(jsonPath("$.lifetimeViews").value(12))
        .andExpect(jsonPath("$.windowViews").value(7))
        .andExpect(jsonPath("$.daily[0].views").value(5));
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
