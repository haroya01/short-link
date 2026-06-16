package com.example.short_link.post.presentation;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.post.application.read.MyCommentView;
import com.example.short_link.post.application.read.PostCommentQueryService;
import com.example.short_link.post.application.write.CreateCommentUseCase;
import com.example.short_link.post.application.write.DeleteCommentUseCase;
import com.example.short_link.post.application.write.LikeCommentUseCase;
import com.example.short_link.testsupport.KurlWebMvcTest;
import com.example.short_link.testsupport.WebMvcSecurityTestConfig;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** HTTP 매핑·status·인증 게이트만 — 검증/소유권 규칙은 서비스 단위 테스트가 진짜로 돈다. */
@KurlWebMvcTest(controllers = CommentController.class)
class CommentControllerTest {

  @Autowired private MockMvc mvc;

  @MockitoBean private CreateCommentUseCase createComment;
  @MockitoBean private DeleteCommentUseCase deleteComment;
  @MockitoBean private LikeCommentUseCase likeComment;
  @MockitoBean private PostCommentQueryService commentQuery;

  private static final long USER_ID = 7L;

  @Test
  void myCommentsReturnsLibraryWithPostContext() throws Exception {
    when(commentQuery.listMyComments(USER_ID))
        .thenReturn(
            List.of(
                new MyCommentView(
                    10L,
                    "내 댓글",
                    null,
                    4L,
                    Instant.parse("2026-06-12T00:00:00Z"),
                    "slug-5",
                    "Title 5",
                    "bob")));

    mvc.perform(
            get("/api/v1/users/me/comments")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(10))
        .andExpect(jsonPath("$[0].body").value("내 댓글"))
        .andExpect(jsonPath("$[0].likeCount").value(4))
        .andExpect(jsonPath("$[0].postSlug").value("slug-5"))
        .andExpect(jsonPath("$[0].postTitle").value("Title 5"))
        .andExpect(jsonPath("$[0].postUsername").value("bob"));
  }

  @Test
  void anonymousMyCommentsIs401() throws Exception {
    mvc.perform(get("/api/v1/users/me/comments")).andExpect(status().isUnauthorized());
  }
}
