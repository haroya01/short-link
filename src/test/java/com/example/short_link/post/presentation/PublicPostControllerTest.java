package com.example.short_link.post.presentation;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.post.application.read.PublicAuthorView;
import com.example.short_link.post.application.read.PublicPostBlockView;
import com.example.short_link.post.application.read.PublicPostDetail;
import com.example.short_link.post.application.read.PublicPostListItem;
import com.example.short_link.post.application.read.PublicPostListView;
import com.example.short_link.post.application.read.PublicPostQueryService;
import com.example.short_link.post.exception.PostErrorCode;
import com.example.short_link.post.exception.PostException;
import com.example.short_link.profile.exception.ProfileErrorCode;
import com.example.short_link.profile.exception.ProfileException;
import com.example.short_link.profile.presentation.ProfileExceptionHandler;
import com.example.short_link.testsupport.KurlWebMvcTest;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@KurlWebMvcTest(controllers = PublicPostController.class)
@Import({PostExceptionHandler.class, ProfileExceptionHandler.class})
class PublicPostControllerTest {

  @Autowired private MockMvc mvc;

  @MockitoBean private PublicPostQueryService publicPostQueryService;

  private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

  @Test
  void listPublicPostsNoAuthRequired() throws Exception {
    PublicPostListView response =
        new PublicPostListView(
            new PublicAuthorView(7L, "john", "Bio", "https://cdn/avatar.png"),
            List.of(
                new PublicPostListItem(
                    1L, "post-1", "Post 1", "Excerpt", null, "ko", List.of("spring", "jpa"), NOW),
                new PublicPostListItem(2L, "post-2", "Post 2", null, null, "ja", List.of(), NOW)));
    when(publicPostQueryService.listPublicPosts("john")).thenReturn(response);

    mvc.perform(get("/api/v1/public/profiles/john/posts"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.author.username").value("john"))
        .andExpect(jsonPath("$.posts[0].slug").value("post-1"))
        .andExpect(jsonPath("$.posts[0].tags[0]").value("spring"))
        .andExpect(jsonPath("$.posts[1].slug").value("post-2"));
  }

  @Test
  void listUnknownUsernameReturns404() throws Exception {
    when(publicPostQueryService.listPublicPosts("ghost"))
        .thenThrow(new ProfileException(ProfileErrorCode.PROFILE_NOT_FOUND, "ghost"));

    mvc.perform(get("/api/v1/public/profiles/ghost/posts"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("PROFILE_NOT_FOUND"));
  }

  @Test
  void findPublicPostReturnsDetail() throws Exception {
    PublicPostDetail detail =
        new PublicPostDetail(
            new PublicAuthorView(7L, "john", "Bio", "https://cdn/avatar.png"),
            new PublicPostListItem(
                10L, "first-post", "First", "Excerpt", null, "ko", List.of(), NOW),
            List.of(new PublicPostBlockView("PARAGRAPH", "Hello", 0, null)),
            null);
    when(publicPostQueryService.findPublicPost("john", "first-post")).thenReturn(detail);

    mvc.perform(get("/api/v1/public/profiles/john/posts/first-post"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.post.slug").value("first-post"))
        .andExpect(jsonPath("$.blocks[0].type").value("PARAGRAPH"))
        .andExpect(jsonPath("$.blocks[0].content").value("Hello"));
  }

  @Test
  void findDraftReturns404() throws Exception {
    when(publicPostQueryService.findPublicPost("john", "draft"))
        .thenThrow(new PostException(PostErrorCode.POST_NOT_FOUND, "draft"));

    mvc.perform(get("/api/v1/public/profiles/john/posts/draft"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
  }

  @Test
  void findUnpublishedReturns410Gone() throws Exception {
    when(publicPostQueryService.findPublicPost("john", "gone"))
        .thenThrow(new PostException(PostErrorCode.POST_GONE, "gone"));

    mvc.perform(get("/api/v1/public/profiles/john/posts/gone"))
        .andExpect(status().isGone())
        .andExpect(jsonPath("$.code").value("POST_GONE"));
  }
}
