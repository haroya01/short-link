package com.example.short_link.post.presentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.post.application.read.PostQueryService;
import com.example.short_link.post.application.read.PostView;
import com.example.short_link.post.application.write.BackToDraftPostCommand;
import com.example.short_link.post.application.write.BackToDraftPostUseCase;
import com.example.short_link.post.application.write.CreatePostCommand;
import com.example.short_link.post.application.write.CreatePostUseCase;
import com.example.short_link.post.application.write.PublishPostCommand;
import com.example.short_link.post.application.write.PublishPostUseCase;
import com.example.short_link.post.application.write.RepublishPostCommand;
import com.example.short_link.post.application.write.RepublishPostUseCase;
import com.example.short_link.post.application.write.SchedulePostCommand;
import com.example.short_link.post.application.write.SchedulePostUseCase;
import com.example.short_link.post.application.write.UnpublishPostCommand;
import com.example.short_link.post.application.write.UnpublishPostUseCase;
import com.example.short_link.post.application.write.UpdatePostMetadataCommand;
import com.example.short_link.post.application.write.UpdatePostMetadataUseCase;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.exception.PostErrorCode;
import com.example.short_link.post.exception.PostException;
import com.example.short_link.testsupport.KurlWebMvcTest;
import com.example.short_link.testsupport.WebMvcSecurityTestConfig;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@KurlWebMvcTest(controllers = PostController.class)
@Import(PostExceptionHandler.class)
class PostControllerTest {

  @Autowired private MockMvc mvc;

  @MockitoBean private CreatePostUseCase createPost;
  @MockitoBean private UpdatePostMetadataUseCase updatePostMetadata;
  @MockitoBean private PublishPostUseCase publishPost;
  @MockitoBean private SchedulePostUseCase schedulePost;
  @MockitoBean private UnpublishPostUseCase unpublishPost;
  @MockitoBean private RepublishPostUseCase republishPost;
  @MockitoBean private BackToDraftPostUseCase backToDraftPost;
  @MockitoBean private PostQueryService postQueryService;

  private static final long USER_ID = 7L;

  @Test
  void anonymousCreateIs401() throws Exception {
    mvc.perform(
            post("/api/v1/posts")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"slug\":\"first-post\",\"title\":\"First\",\"languageTag\":\"ko\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void createsDraftPost() throws Exception {
    PostEntity saved = new PostEntity(USER_ID, "first-post", "First", "ko");
    when(createPost.execute(any(CreatePostCommand.class))).thenReturn(saved);

    mvc.perform(
            post("/api/v1/posts")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"slug\":\"first-post\",\"title\":\"First\",\"languageTag\":\"ko\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.slug").value("first-post"))
        .andExpect(jsonPath("$.title").value("First"))
        .andExpect(jsonPath("$.status").value("DRAFT"))
        .andExpect(jsonPath("$.languageTag").value("ko"));
  }

  @Test
  void slugConflictReturns409() throws Exception {
    when(createPost.execute(any(CreatePostCommand.class)))
        .thenThrow(new PostException(PostErrorCode.SLUG_CONFLICT, "taken"));

    mvc.perform(
            post("/api/v1/posts")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"slug\":\"taken\",\"title\":\"Title\",\"languageTag\":\"ko\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SLUG_CONFLICT"));
  }

  @Test
  void invalidCreateRequestReturns400() throws Exception {
    mvc.perform(
            post("/api/v1/posts")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"slug\":\"\",\"title\":\"\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void listMineReturnsViews() throws Exception {
    PostView v1 = PostView.from(new PostEntity(USER_ID, "post-1", "Post 1", "ko"));
    PostView v2 = PostView.from(new PostEntity(USER_ID, "post-2", "Post 2", "ja"));
    when(postQueryService.listMyPosts(USER_ID)).thenReturn(List.of(v1, v2));

    mvc.perform(get("/api/v1/posts").header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].slug").value("post-1"))
        .andExpect(jsonPath("$[1].slug").value("post-2"));
  }

  @Test
  void anonymousListIs401() throws Exception {
    mvc.perform(get("/api/v1/posts")).andExpect(status().isUnauthorized());
  }

  @Test
  void findByIdReturnsView() throws Exception {
    PostView view = PostView.from(new PostEntity(USER_ID, "my-post", "My Post", "ko"));
    when(postQueryService.findOwnPost(USER_ID, 42L)).thenReturn(view);

    mvc.perform(get("/api/v1/posts/42").header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.slug").value("my-post"))
        .andExpect(jsonPath("$.title").value("My Post"));
  }

  @Test
  void findByIdNotFoundReturns404() throws Exception {
    when(postQueryService.findOwnPost(USER_ID, 99L))
        .thenThrow(new PostException(PostErrorCode.POST_NOT_FOUND, 99L));

    mvc.perform(get("/api/v1/posts/99").header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
  }

  @Test
  void findByIdOtherOwnerReturns403() throws Exception {
    when(postQueryService.findOwnPost(USER_ID, 42L))
        .thenThrow(new PostException(PostErrorCode.PERMISSION_DENIED));

    mvc.perform(get("/api/v1/posts/42").header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
  }

  @Test
  void patchUpdatesTitle() throws Exception {
    PostEntity updated = new PostEntity(USER_ID, "my-post", "Updated Title", "ko");
    when(updatePostMetadata.execute(any(UpdatePostMetadataCommand.class))).thenReturn(updated);

    mvc.perform(
            patch("/api/v1/posts/42")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Updated Title\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("Updated Title"));
  }

  @Test
  void patchSlugFrozenReturns409() throws Exception {
    when(updatePostMetadata.execute(any(UpdatePostMetadataCommand.class)))
        .thenThrow(new PostException(PostErrorCode.SLUG_FROZEN, "original-slug"));

    mvc.perform(
            patch("/api/v1/posts/42")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"slug\":\"new-slug\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SLUG_FROZEN"));
  }

  @Test
  void publishReturnsPublishedView() throws Exception {
    PostEntity published = new PostEntity(USER_ID, "my-post", "Title", "ko");
    published.publish();
    when(publishPost.execute(any(PublishPostCommand.class))).thenReturn(published);

    mvc.perform(
            post("/api/v1/posts/42/publish")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PUBLISHED"));
  }

  @Test
  void scheduleAcceptsFutureInstant() throws Exception {
    PostEntity scheduled = new PostEntity(USER_ID, "my-post", "Title", "ko");
    Instant when = Instant.now().plus(2, ChronoUnit.HOURS);
    scheduled.schedule(when);
    when(schedulePost.execute(any(SchedulePostCommand.class))).thenReturn(scheduled);

    mvc.perform(
            post("/api/v1/posts/42/schedule")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"scheduledAt\":\"" + when + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SCHEDULED"));
  }

  @Test
  void unpublishReturnsUnpublishedView() throws Exception {
    PostEntity unpublished = new PostEntity(USER_ID, "my-post", "Title", "ko");
    unpublished.publish();
    unpublished.unpublish();
    when(unpublishPost.execute(any(UnpublishPostCommand.class))).thenReturn(unpublished);

    mvc.perform(
            post("/api/v1/posts/42/unpublish")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UNPUBLISHED"));
  }

  @Test
  void republishOnDraftReturns409() throws Exception {
    when(republishPost.execute(any(RepublishPostCommand.class)))
        .thenThrow(new PostException(PostErrorCode.REPUBLISH_NOT_UNPUBLISHED));

    mvc.perform(
            post("/api/v1/posts/42/republish")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("REPUBLISH_NOT_UNPUBLISHED"));
  }

  @Test
  void backToDraftReturnsDraftView() throws Exception {
    PostEntity draft = new PostEntity(USER_ID, "my-post", "Title", "ko");
    when(backToDraftPost.execute(any(BackToDraftPostCommand.class))).thenReturn(draft);

    mvc.perform(
            post("/api/v1/posts/42/back-to-draft")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DRAFT"));
  }
}
