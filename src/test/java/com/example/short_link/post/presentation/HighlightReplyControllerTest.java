package com.example.short_link.post.presentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.post.application.read.HighlightReplyView;
import com.example.short_link.post.application.read.PublicAuthorView;
import com.example.short_link.post.application.write.CreateHighlightReplyCommand;
import com.example.short_link.post.application.write.CreateHighlightReplyUseCase;
import com.example.short_link.post.application.write.DeleteHighlightReplyCommand;
import com.example.short_link.post.application.write.DeleteHighlightReplyUseCase;
import com.example.short_link.testsupport.KurlWebMvcTest;
import com.example.short_link.testsupport.WebMvcSecurityTestConfig;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** HTTP 매핑·status·인증 게이트만 — 검증/소유권 규칙은 use-case 단위 테스트가 진짜로 돈다. */
@KurlWebMvcTest(controllers = HighlightReplyController.class)
class HighlightReplyControllerTest {

  @Autowired private MockMvc mvc;

  @MockitoBean private CreateHighlightReplyUseCase createReply;
  @MockitoBean private DeleteHighlightReplyUseCase deleteReply;

  private static final long USER_ID = 7L;

  @Test
  void createReturns201() throws Exception {
    when(createReply.execute(any(CreateHighlightReplyCommand.class)))
        .thenReturn(
            new HighlightReplyView(
                10L,
                new PublicAuthorView(USER_ID, "kim", null, null),
                "동의합니다",
                Instant.parse("2026-06-12T00:00:00Z")));

    mvc.perform(
            post("/api/v1/highlights/5/replies")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"동의합니다\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(10))
        .andExpect(jsonPath("$.author.username").value("kim"))
        .andExpect(jsonPath("$.body").value("동의합니다"));
  }

  @Test
  void createRejectsBlankBody() throws Exception {
    mvc.perform(
            post("/api/v1/highlights/5/replies")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void anonymousCreateIs401() throws Exception {
    mvc.perform(
            post("/api/v1/highlights/5/replies")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"hi\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void deleteReturns204() throws Exception {
    mvc.perform(
            delete("/api/v1/highlight-replies/9")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID))
        .andExpect(status().isNoContent());

    verify(deleteReply).execute(any(DeleteHighlightReplyCommand.class));
  }

  @Test
  void anonymousDeleteIs401() throws Exception {
    mvc.perform(delete("/api/v1/highlight-replies/9")).andExpect(status().isUnauthorized());
  }
}
