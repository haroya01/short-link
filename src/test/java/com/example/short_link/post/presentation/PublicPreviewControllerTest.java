package com.example.short_link.post.presentation;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.post.application.read.PublicAuthorView;
import com.example.short_link.post.application.read.PublicPostBlockView;
import com.example.short_link.post.application.read.PublicPostDetail;
import com.example.short_link.post.application.read.PublicPostListItem;
import com.example.short_link.post.application.read.PublicPostQueryService;
import com.example.short_link.post.exception.PostErrorCode;
import com.example.short_link.post.exception.PostException;
import com.example.short_link.testsupport.KurlWebMvcTest;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@KurlWebMvcTest(controllers = PublicPreviewController.class)
@Import(PostExceptionHandler.class)
class PublicPreviewControllerTest {

  @Autowired private MockMvc mvc;

  @MockitoBean private PublicPostQueryService publicPostQueryService;

  private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

  @Test
  void previewByTokenReturnsDetailNoAuthRequired() throws Exception {
    PublicPostDetail detail =
        new PublicPostDetail(
            new PublicAuthorView(7L, "john", "Bio", "https://cdn/avatar.png"),
            new PublicPostListItem(
                10L, "draft-post", "Draft", "Excerpt", null, "ko", List.of(), 0L, NOW, null, false),
            List.of(new PublicPostBlockView("PARAGRAPH", "Hello", 0, null)),
            null);
    when(publicPostQueryService.findPreviewPost("tok-123")).thenReturn(detail);

    mvc.perform(get("/api/v1/public/preview/tok-123"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.post.slug").value("draft-post"))
        .andExpect(jsonPath("$.blocks[0].content").value("Hello"));
  }

  @Test
  void previewUnknownTokenReturns404() throws Exception {
    when(publicPostQueryService.findPreviewPost("nope"))
        .thenThrow(new PostException(PostErrorCode.POST_NOT_FOUND, ""));

    mvc.perform(get("/api/v1/public/preview/nope"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
  }
}
