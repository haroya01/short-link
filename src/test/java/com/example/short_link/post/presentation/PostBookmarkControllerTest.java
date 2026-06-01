package com.example.short_link.post.presentation;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.post.application.read.BookmarkView;
import com.example.short_link.post.application.read.PostBookmarkQueryService;
import com.example.short_link.post.application.read.PostBookmarkStatus;
import com.example.short_link.post.application.write.BookmarkPostUseCase;
import com.example.short_link.testsupport.KurlWebMvcTest;
import com.example.short_link.testsupport.WebMvcSecurityTestConfig;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@KurlWebMvcTest(controllers = PostBookmarkController.class)
class PostBookmarkControllerTest {

  @Autowired private MockMvc mvc;

  @MockitoBean private BookmarkPostUseCase bookmarkPost;
  @MockitoBean private PostBookmarkQueryService postBookmarkQueryService;

  private static final long USER_ID = 7L;

  @Test
  void statusReturnsBookmarkedFlag() throws Exception {
    when(postBookmarkQueryService.status(eq(USER_ID), eq(42L)))
        .thenReturn(new PostBookmarkStatus(true));

    mvc.perform(
            get("/api/v1/posts/42/bookmark")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.bookmarked").value(true));
  }

  @Test
  void bookmarkReturnsTrue() throws Exception {
    when(bookmarkPost.bookmark(eq(USER_ID), eq(42L))).thenReturn(new PostBookmarkStatus(true));

    mvc.perform(
            put("/api/v1/posts/42/bookmark")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.bookmarked").value(true));
  }

  @Test
  void unbookmarkReturnsFalse() throws Exception {
    when(bookmarkPost.unbookmark(eq(USER_ID), eq(42L))).thenReturn(new PostBookmarkStatus(false));

    mvc.perform(
            delete("/api/v1/posts/42/bookmark")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.bookmarked").value(false));
  }

  @Test
  void myBookmarksReturnsList() throws Exception {
    when(postBookmarkQueryService.list(eq(USER_ID)))
        .thenReturn(List.of(new BookmarkView(42L, "alice", "My Post", "my-post")));

    mvc.perform(get("/api/v1/bookmarks").header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(42))
        .andExpect(jsonPath("$[0].username").value("alice"))
        .andExpect(jsonPath("$[0].slug").value("my-post"));
  }

  @Test
  void anonymousBookmarkIs401() throws Exception {
    mvc.perform(put("/api/v1/posts/42/bookmark")).andExpect(status().isUnauthorized());
  }
}
