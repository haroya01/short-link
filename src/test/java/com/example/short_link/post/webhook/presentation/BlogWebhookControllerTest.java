package com.example.short_link.post.webhook.presentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.common.event.BlogInteractionType;
import com.example.short_link.post.exception.PostErrorCode;
import com.example.short_link.post.exception.PostException;
import com.example.short_link.post.presentation.PostExceptionHandler;
import com.example.short_link.post.webhook.application.dto.IssuedBlogWebhook;
import com.example.short_link.post.webhook.application.read.BlogWebhookQueryService;
import com.example.short_link.post.webhook.application.write.DeleteBlogWebhookUseCase;
import com.example.short_link.post.webhook.application.write.RegisterBlogWebhookUseCase;
import com.example.short_link.post.webhook.application.write.UpdateBlogWebhookUseCase;
import com.example.short_link.post.webhook.domain.BlogWebhookFormat;
import com.example.short_link.testsupport.KurlWebMvcTest;
import com.example.short_link.testsupport.WebMvcSecurityTestConfig;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@KurlWebMvcTest(controllers = BlogWebhookController.class)
@Import(PostExceptionHandler.class)
class BlogWebhookControllerTest {

  @Autowired private MockMvc mvc;

  @MockitoBean private BlogWebhookQueryService queryService;
  @MockitoBean private RegisterBlogWebhookUseCase registerUseCase;
  @MockitoBean private UpdateBlogWebhookUseCase updateUseCase;
  @MockitoBean private DeleteBlogWebhookUseCase deleteUseCase;

  private static final long USER_ID = 9L;

  @Test
  void listReturnsOk() throws Exception {
    when(queryService.listForUser(USER_ID)).thenReturn(List.of());

    mvc.perform(
            get("/api/v1/blog/webhooks").header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID))
        .andExpect(status().isOk());
  }

  @Test
  void registerIssuesSecret() throws Exception {
    when(registerUseCase.execute(eq(USER_ID), eq("https://example.com/h"), any(), any()))
        .thenReturn(
            new IssuedBlogWebhook(
                1L,
                "https://example.com/h",
                "sekret",
                "hook",
                BlogWebhookFormat.GENERIC,
                Set.of(BlogInteractionType.LIKE),
                Instant.parse("2026-06-04T10:00:00Z")));

    mvc.perform(
            post("/api/v1/blog/webhooks")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID)
                .contentType("application/json")
                .content(
                    "{\"url\":\"https://example.com/h\",\"name\":\"hook\",\"events\":[\"LIKE\"]}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.secret").value("sekret"))
        .andExpect(jsonPath("$.format").value("GENERIC"));
  }

  @Test
  void registerRejectsBlankUrlWith400() throws Exception {
    mvc.perform(
            post("/api/v1/blog/webhooks")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID)
                .contentType("application/json")
                .content("{\"url\":\"\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void deleteReturns204() throws Exception {
    mvc.perform(
            delete("/api/v1/blog/webhooks/5")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID))
        .andExpect(status().isNoContent());
    verify(deleteUseCase).execute(USER_ID, 5L);
  }

  @Test
  void updateUnknownHookMapsTo404() throws Exception {
    when(updateUseCase.execute(eq(USER_ID), eq(5L), any(), any(), any()))
        .thenThrow(new PostException(PostErrorCode.WEBHOOK_NOT_FOUND, 5L));

    mvc.perform(
            patch("/api/v1/blog/webhooks/5")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID)
                .contentType("application/json")
                .content("{\"enabled\":false}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("WEBHOOK_NOT_FOUND"));
  }

  @Test
  void anonymousIs401() throws Exception {
    mvc.perform(get("/api/v1/blog/webhooks")).andExpect(status().isUnauthorized());
  }
}
