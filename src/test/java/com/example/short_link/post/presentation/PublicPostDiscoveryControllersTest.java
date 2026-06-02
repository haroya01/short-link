package com.example.short_link.post.presentation;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.post.application.read.CommentView;
import com.example.short_link.post.application.read.PostCommentQueryService;
import com.example.short_link.post.application.read.PublicAuthorView;
import com.example.short_link.post.application.read.PublicFeedQueryService;
import com.example.short_link.post.application.read.PublicFeedView;
import com.example.short_link.post.application.read.PublicSeriesQueryService;
import com.example.short_link.post.application.read.SuggestedAuthorView;
import com.example.short_link.post.application.read.TrendingTagSection;
import com.example.short_link.testsupport.KurlWebMvcTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** 인증 없는 공개 발견 컨트롤러 슬라이스 — query 라우팅 + size 클램프 + 직렬화. */
@KurlWebMvcTest(
    controllers = {
      PublicFeedController.class,
      PublicTrendingController.class,
      PublicAuthorController.class,
      PublicSeriesDiscoveryController.class,
      PublicCommentController.class
    })
class PublicPostDiscoveryControllersTest {

  @Autowired private MockMvc mvc;

  @MockitoBean private PublicFeedQueryService publicFeedQueryService;
  @MockitoBean private PublicSeriesQueryService publicSeriesQueryService;
  @MockitoBean private PostCommentQueryService postCommentQueryService;

  private PublicFeedView emptyFeed() {
    return new PublicFeedView(List.of(), 0, 20, false);
  }

  @Test
  void feedDefaultUsesRecentSort() throws Exception {
    when(publicFeedQueryService.feed("recent", 0, 20)).thenReturn(emptyFeed());

    mvc.perform(get("/api/v1/public/posts"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.hasNext").value(false))
        .andExpect(jsonPath("$.items.length()").value(0));

    verify(publicFeedQueryService).feed("recent", 0, 20);
  }

  @Test
  void feedWithQueryRoutesToSearch() throws Exception {
    when(publicFeedQueryService.search("hello", "recent", 0, 20)).thenReturn(emptyFeed());

    mvc.perform(get("/api/v1/public/posts").param("q", " hello ")).andExpect(status().isOk());

    verify(publicFeedQueryService).search("hello", "recent", 0, 20);
  }

  @Test
  void feedWithTagRoutesToFeedByTag() throws Exception {
    when(publicFeedQueryService.feedByTag("java", 0, 20)).thenReturn(emptyFeed());

    mvc.perform(get("/api/v1/public/posts").param("tag", " java ")).andExpect(status().isOk());

    verify(publicFeedQueryService).feedByTag("java", 0, 20);
  }

  @Test
  void feedClampsOversizedPageSizeTo50() throws Exception {
    when(publicFeedQueryService.feed("recent", 0, 50)).thenReturn(emptyFeed());

    mvc.perform(get("/api/v1/public/posts").param("size", "999").param("page", "-3"))
        .andExpect(status().isOk());

    verify(publicFeedQueryService).feed("recent", 0, 50);
  }

  @Test
  void trendingByTagClampsAndReturnsSections() throws Exception {
    when(publicFeedQueryService.trendingByTag(6, 8))
        .thenReturn(List.of(new TrendingTagSection("java", 3, List.of())));

    mvc.perform(get("/api/v1/public/feed/trending-by-tag"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].tag").value("java"));
  }

  @Test
  void suggestedAuthorsReturnsList() throws Exception {
    when(publicFeedQueryService.suggestedAuthors(3))
        .thenReturn(
            List.of(
                new SuggestedAuthorView(
                    new PublicAuthorView(1L, "kim", "bio", "https://a/x.png"), 9)));

    mvc.perform(get("/api/v1/public/authors").param("limit", "3"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].author.username").value("kim"))
        .andExpect(jsonPath("$[0].postCount").value(9));
  }

  @Test
  void discoverSeriesReturnsList() throws Exception {
    when(publicSeriesQueryService.discoverSeries(anyInt())).thenReturn(List.of());

    mvc.perform(get("/api/v1/public/series"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void listCommentsReturnsComments() throws Exception {
    when(postCommentQueryService.listForPost(5L))
        .thenReturn(
            List.of(
                new CommentView(
                    1L,
                    null,
                    new PublicAuthorView(2L, "lee", null, null),
                    "nice post",
                    java.time.Instant.parse("2026-01-01T00:00:00Z"))));

    mvc.perform(get("/api/v1/public/posts/5/comments"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].body").value("nice post"))
        .andExpect(jsonPath("$[0].author.username").value("lee"));
  }
}
