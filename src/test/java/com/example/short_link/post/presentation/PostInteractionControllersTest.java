package com.example.short_link.post.presentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.post.application.image.PostImageService;
import com.example.short_link.post.application.read.CommentView;
import com.example.short_link.post.application.read.PostLikeQueryService;
import com.example.short_link.post.application.read.PostLikeStatus;
import com.example.short_link.post.application.read.PublicAuthorView;
import com.example.short_link.post.application.read.PublicFeedQueryService;
import com.example.short_link.post.application.read.PublicFeedView;
import com.example.short_link.post.application.write.CreateCommentCommand;
import com.example.short_link.post.application.write.CreateCommentUseCase;
import com.example.short_link.post.application.write.DeleteCommentCommand;
import com.example.short_link.post.application.write.DeleteCommentUseCase;
import com.example.short_link.post.application.write.LikePostUseCase;
import com.example.short_link.post.application.write.RecordPostViewCommand;
import com.example.short_link.post.application.write.RecordPostViewUseCase;
import com.example.short_link.post.application.write.ViewContext;
import com.example.short_link.testsupport.KurlWebMvcTest;
import com.example.short_link.testsupport.WebMvcSecurityTestConfig;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** 인증이 필요한 post 상호작용 컨트롤러 + 공개 view beacon 슬라이스. */
@KurlWebMvcTest(
    controllers = {
      CommentController.class,
      PostLikeController.class,
      PostImageController.class,
      FollowingFeedController.class,
      PublicPostViewBeaconController.class
    })
class PostInteractionControllersTest {

  @Autowired private MockMvc mvc;

  @MockitoBean private CreateCommentUseCase createComment;
  @MockitoBean private DeleteCommentUseCase deleteComment;
  @MockitoBean private LikePostUseCase likePost;
  @MockitoBean private PostLikeQueryService postLikeQueryService;
  @MockitoBean private PostImageService postImageService;
  @MockitoBean private PublicFeedQueryService publicFeedQueryService;
  @MockitoBean private RecordPostViewUseCase recordPostView;

  private static final long USER_ID = 7L;

  @Test
  void createCommentRejectsAnonymous() throws Exception {
    mvc.perform(
            post("/api/v1/posts/3/comments")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"hi\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void createCommentReturns201() throws Exception {
    when(createComment.execute(any(CreateCommentCommand.class)))
        .thenReturn(
            new CommentView(
                10L,
                null,
                new PublicAuthorView(USER_ID, "kim", null, null),
                "hi there",
                Instant.parse("2026-01-01T00:00:00Z")));

    mvc.perform(
            post("/api/v1/posts/3/comments")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"hi there\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.body").value("hi there"));
  }

  @Test
  void createCommentRejectsBlankBody() throws Exception {
    mvc.perform(
            post("/api/v1/posts/3/comments")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void deleteCommentReturns204() throws Exception {
    mvc.perform(
            delete("/api/v1/comments/9").header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID))
        .andExpect(status().isNoContent());

    verify(deleteComment).execute(any(DeleteCommentCommand.class));
  }

  @Test
  void likeStatusReturnsCount() throws Exception {
    when(postLikeQueryService.status(USER_ID, 3L)).thenReturn(new PostLikeStatus(12, true));

    mvc.perform(
            get("/api/v1/posts/3/like").header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.likeCount").value(12))
        .andExpect(jsonPath("$.liked").value(true));
  }

  @Test
  void likePutInvokesLike() throws Exception {
    when(likePost.like(USER_ID, 3L)).thenReturn(new PostLikeStatus(13, true));

    mvc.perform(
            put("/api/v1/posts/3/like").header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.likeCount").value(13));
  }

  @Test
  void unlikeDeleteInvokesUnlike() throws Exception {
    when(likePost.unlike(USER_ID, 3L)).thenReturn(new PostLikeStatus(11, false));

    mvc.perform(
            delete("/api/v1/posts/3/like").header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.liked").value(false));
  }

  @Test
  void likeRejectsAnonymous() throws Exception {
    mvc.perform(get("/api/v1/posts/3/like")).andExpect(status().isUnauthorized());
  }

  @Test
  void presignReturnsUploadUrl() throws Exception {
    when(postImageService.presignUpload(eq(USER_ID), eq(3L), eq("image/png")))
        .thenReturn(
            new PostImageService.PresignResult(
                "https://s3/upload", "https://cdn/x.png", "k/x.png", "image/png", 1024, 600));

    mvc.perform(
            post("/api/v1/posts/3/images/presign")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"contentType\":\"image/png\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.uploadUrl").value("https://s3/upload"))
        .andExpect(jsonPath("$.key").value("k/x.png"));
  }

  @Test
  void commitReturnsImageUrl() throws Exception {
    when(postImageService.commitUpload(eq(USER_ID), eq(3L), eq("k/x.png")))
        .thenReturn(new PostImageService.CommitResult("https://cdn/x.png", "k/x.png"));

    mvc.perform(
            post("/api/v1/posts/3/images/commit")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"key\":\"k/x.png\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.imageUrl").value("https://cdn/x.png"));
  }

  @Test
  void presignRejectsBlankContentType() throws Exception {
    mvc.perform(
            post("/api/v1/posts/3/images/presign")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"contentType\":\"\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void followingFeedRejectsAnonymous() throws Exception {
    mvc.perform(get("/api/v1/feed/following")).andExpect(status().isUnauthorized());
  }

  @Test
  void followingFeedReturnsFeed() throws Exception {
    when(publicFeedQueryService.feedFollowing(USER_ID, 0, 20))
        .thenReturn(new PublicFeedView(List.of(), 0, 20, false));

    mvc.perform(
            get("/api/v1/feed/following").header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.hasNext").value(false));

    verify(publicFeedQueryService).feedFollowing(USER_ID, 0, 20);
  }

  @Test
  void viewBeaconReturns202() throws Exception {
    // controller 가 principal 을 쓰지 않으므로 헤더는 test 필터 통과용.
    mvc.perform(
            post("/api/v1/public/profiles/kim/posts/my-slug/view")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID))
        .andExpect(status().isAccepted());

    verify(recordPostView).execute(any(RecordPostViewCommand.class), any(ViewContext.class));
  }
}
