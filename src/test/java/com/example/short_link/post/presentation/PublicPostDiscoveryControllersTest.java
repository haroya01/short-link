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
import com.example.short_link.post.application.read.PublicFeedQuery;
import com.example.short_link.post.application.read.PublicFeedQuery.Browse;
import com.example.short_link.post.application.read.PublicFeedQuery.BrowseOrder;
import com.example.short_link.post.application.read.PublicFeedQuery.Search;
import com.example.short_link.post.application.read.PublicFeedQuery.SearchOrder;
import com.example.short_link.post.application.read.PublicFeedQuery.Tagged;
import com.example.short_link.post.application.read.PublicFeedQueryService;
import com.example.short_link.post.application.read.PublicFeedView;
import com.example.short_link.post.application.read.PublicSeriesQueryService;
import com.example.short_link.post.application.read.SuggestedAuthorView;
import com.example.short_link.post.application.read.TrendingTagSection;
import com.example.short_link.testsupport.KurlWebMvcTest;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
    var query = new PublicFeedQuery(new Browse(BrowseOrder.RECENT, null), 0, 20);
    when(publicFeedQueryService.feed(query)).thenReturn(emptyFeed());

    mvc.perform(get("/api/v1/public/posts"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.hasNext").value(false))
        .andExpect(jsonPath("$.items.length()").value(0));

    verify(publicFeedQueryService).feed(query);
  }

  @Test
  void feedWithQueryDefaultsToRelevanceSort() throws Exception {
    var query = new PublicFeedQuery(new Search("hello", SearchOrder.RELEVANCE, null), 0, 20);
    when(publicFeedQueryService.feed(query)).thenReturn(emptyFeed());

    mvc.perform(get("/api/v1/public/posts").param("q", " hello ")).andExpect(status().isOk());

    verify(publicFeedQueryService).feed(query);
  }

  @Test
  void feedWithQueryHonorsExplicitSort() throws Exception {
    var query = new PublicFeedQuery(new Search("hello", SearchOrder.RECENT, null), 0, 20);
    when(publicFeedQueryService.feed(query)).thenReturn(emptyFeed());

    mvc.perform(get("/api/v1/public/posts").param("q", "hello").param("sort", "recent"))
        .andExpect(status().isOk());

    verify(publicFeedQueryService).feed(query);
  }

  @Test
  void feedWithTagRoutesToFeedByTag() throws Exception {
    var query = new PublicFeedQuery(new Tagged("java"), 0, 20);
    when(publicFeedQueryService.feed(query)).thenReturn(emptyFeed());

    mvc.perform(get("/api/v1/public/posts").param("tag", " java ")).andExpect(status().isOk());

    verify(publicFeedQueryService).feed(query);
  }

  @Test
  void feedClampsOversizedPageSizeTo50() throws Exception {
    var query = new PublicFeedQuery(new Browse(BrowseOrder.RECENT, null), 0, 50);
    when(publicFeedQueryService.feed(query)).thenReturn(emptyFeed());

    mvc.perform(get("/api/v1/public/posts").param("size", "999").param("page", "-3"))
        .andExpect(status().isOk());

    verify(publicFeedQueryService).feed(query);
  }

  @Test
  void searchTakesPriorityOverTagAndKeepsTheLanguageFilter() throws Exception {
    var query = new PublicFeedQuery(new Search("hello", SearchOrder.TRENDING, " ko "), 2, 8);
    when(publicFeedQueryService.feed(query)).thenReturn(emptyFeed());

    mvc.perform(
            get("/api/v1/public/posts")
                .param("q", " hello ")
                .param("tag", "java")
                .param("sort", "TrEnDiNg")
                .param("lang", " ko ")
                .param("page", "2")
                .param("size", "8"))
        .andExpect(status().isOk());

    verify(publicFeedQueryService).feed(query);
  }

  @Test
  void blankSearchAllowsTagWhichIgnoresLanguageAndSort() throws Exception {
    var query = new PublicFeedQuery(new Tagged("java"), 0, 20);
    when(publicFeedQueryService.feed(query)).thenReturn(emptyFeed());

    mvc.perform(
            get("/api/v1/public/posts")
                .param("q", "  ")
                .param("tag", " java ")
                .param("sort", "trending")
                .param("lang", "ja"))
        .andExpect(status().isOk());

    verify(publicFeedQueryService).feed(query);
  }

  @Test
  void blankFiltersBrowseAndClampNonPositiveSize() throws Exception {
    var query = new PublicFeedQuery(new Browse(BrowseOrder.TRENDING, "  "), 0, 1);
    when(publicFeedQueryService.feed(query)).thenReturn(emptyFeed());

    mvc.perform(
            get("/api/v1/public/posts")
                .param("q", "  ")
                .param("tag", "  ")
                .param("sort", "TRENDING")
                .param("lang", "  ")
                .param("size", "0"))
        .andExpect(status().isOk());

    verify(publicFeedQueryService).feed(query);
  }

  @Test
  void emptyPageParametersUseTheHttpDefaults() throws Exception {
    var query = new PublicFeedQuery(new Browse(BrowseOrder.RECENT, null), 0, 20);
    when(publicFeedQueryService.feed(query)).thenReturn(emptyFeed());

    mvc.perform(get("/api/v1/public/posts").param("page", "").param("size", ""))
        .andExpect(status().isOk());

    verify(publicFeedQueryService).feed(query);
  }

  @Test
  void feedFiltersComeFromParametersAndIgnoreSameNamedHeaders() throws Exception {
    var query = new PublicFeedQuery(new Browse(BrowseOrder.RECENT, null), 0, 20);
    when(publicFeedQueryService.feed(query)).thenReturn(emptyFeed());

    mvc.perform(
            get("/api/v1/public/posts")
                .header("q", "hidden search")
                .header("tag", "hidden tag")
                .header("sort", "trending")
                .header("lang", "ja"))
        .andExpect(status().isOk());

    verify(publicFeedQueryService).feed(query);
  }

  @Test
  void formDefaultPrefixesDoNotSupplyMissingFeedFilters() throws Exception {
    var query = new PublicFeedQuery(new Browse(BrowseOrder.RECENT, null), 0, 20);
    when(publicFeedQueryService.feed(query)).thenReturn(emptyFeed());

    mvc.perform(
            get("/api/v1/public/posts")
                .param("!q", "hidden search")
                .param("!tag", "hidden tag")
                .param("!sort", "trending")
                .param("!lang", "ja"))
        .andExpect(status().isOk());

    verify(publicFeedQueryService).feed(query);
  }

  @ParameterizedTest
  @ValueSource(strings = {"q", "q[]"})
  void repeatedSearchParametersRetainTheirCommaJoinedString(String parameter) throws Exception {
    var query = new PublicFeedQuery(new Search("spring,java", SearchOrder.RECENT, "ko"), 0, 20);
    when(publicFeedQueryService.feed(query)).thenReturn(emptyFeed());

    mvc.perform(
            get("/api/v1/public/posts")
                .param(parameter, "spring", "java")
                .param("sort[]", "recent")
                .param("lang[]", "ko"))
        .andExpect(status().isOk());

    verify(publicFeedQueryService).feed(query);
  }

  @Test
  void emptyNamedSearchTakesPriorityOverItsArrayAlias() throws Exception {
    var query = new PublicFeedQuery(new Tagged("java"), 0, 20);
    when(publicFeedQueryService.feed(query)).thenReturn(emptyFeed());

    mvc.perform(
            get("/api/v1/public/posts")
                .param("q", "")
                .param("q[]", "ignored search")
                .param("tag", "java"))
        .andExpect(status().isOk());

    verify(publicFeedQueryService).feed(query);
  }

  @ParameterizedTest
  @ValueSource(strings = {"page", "size"})
  void nonNumericPageParametersReturnAnInvalidArgument(String parameter) throws Exception {
    mvc.perform(get("/api/v1/public/posts").param(parameter, "later"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"))
        .andExpect(jsonPath("$.parameter").value(parameter));
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
                    Instant.parse("2026-01-01T00:00:00Z"),
                    0L)));

    mvc.perform(get("/api/v1/public/posts/5/comments"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].body").value("nice post"))
        .andExpect(jsonPath("$[0].author.username").value("lee"));
  }
}
