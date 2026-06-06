package com.example.short_link.post.presentation;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.post.application.read.PublicAuthorView;
import com.example.short_link.post.application.read.PublicPostListItem;
import com.example.short_link.post.application.read.PublicSeriesDetail;
import com.example.short_link.post.application.read.PublicSeriesListItem;
import com.example.short_link.post.application.read.PublicSeriesListView;
import com.example.short_link.post.application.read.PublicSeriesQueryService;
import com.example.short_link.post.exception.PostErrorCode;
import com.example.short_link.post.exception.PostException;
import com.example.short_link.profile.presentation.ProfileExceptionHandler;
import com.example.short_link.testsupport.KurlWebMvcTest;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@KurlWebMvcTest(controllers = PublicSeriesController.class)
@Import({PostExceptionHandler.class, ProfileExceptionHandler.class})
class PublicSeriesControllerTest {

  @Autowired private MockMvc mvc;

  @MockitoBean private PublicSeriesQueryService publicSeriesQueryService;

  private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

  @Test
  void listsSeriesNoAuth() throws Exception {
    when(publicSeriesQueryService.listPublicSeries("john"))
        .thenReturn(
            new PublicSeriesListView(
                new PublicAuthorView(7L, "john", "Bio", null),
                List.of(new PublicSeriesListItem(11L, "guide", "Guide", 3, List.of("tag")))));

    mvc.perform(get("/api/v1/public/profiles/john/series"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.series[0].id").value(11))
        .andExpect(jsonPath("$.series[0].slug").value("guide"))
        .andExpect(jsonPath("$.series[0].postCount").value(3));
  }

  @Test
  void seriesDetailReturnsOrderedPosts() throws Exception {
    when(publicSeriesQueryService.findPublicSeries("john", "guide"))
        .thenReturn(
            new PublicSeriesDetail(
                new PublicAuthorView(7L, "john", "Bio", null),
                new PublicSeriesListItem(11L, "guide", "Guide", 1, List.of()),
                List.of(
                    new PublicPostListItem(
                        1L, "intro", "Intro", null, null, "ko", List.of(), 0L, NOW, null, false))));

    mvc.perform(get("/api/v1/public/profiles/john/series/guide"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.series.id").value(11))
        .andExpect(jsonPath("$.series.title").value("Guide"))
        .andExpect(jsonPath("$.posts[0].slug").value("intro"));
  }

  @Test
  void unknownSeriesReturns404() throws Exception {
    when(publicSeriesQueryService.findPublicSeries("john", "ghost"))
        .thenThrow(new PostException(PostErrorCode.SERIES_NOT_FOUND, "ghost"));

    mvc.perform(get("/api/v1/public/profiles/john/series/ghost"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("SERIES_NOT_FOUND"));
  }
}
