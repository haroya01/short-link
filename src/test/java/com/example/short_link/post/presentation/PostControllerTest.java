package com.example.short_link.post.presentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.post.application.write.CreatePostCommand;
import com.example.short_link.post.application.write.CreatePostUseCase;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.exception.PostErrorCode;
import com.example.short_link.post.exception.PostException;
import com.example.short_link.testsupport.KurlWebMvcTest;
import com.example.short_link.testsupport.WebMvcSecurityTestConfig;
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

  private static final long USER_ID = 7L;

  @Test
  void anonymousIs401() throws Exception {
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
  void invalidRequestReturns400() throws Exception {
    mvc.perform(
            post("/api/v1/posts")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"slug\":\"\",\"title\":\"\"}"))
        .andExpect(status().isBadRequest());
  }
}
