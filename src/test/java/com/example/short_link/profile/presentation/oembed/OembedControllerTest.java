package com.example.short_link.profile.presentation.oembed;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.profile.application.oembed.OembedMetadata;
import com.example.short_link.profile.application.oembed.OembedService;
import com.example.short_link.testsupport.KurlWebMvcTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@KurlWebMvcTest(controllers = OembedController.class)
class OembedControllerTest {

  @Autowired private MockMvc mvc;

  @MockitoBean private OembedService service;

  @Test
  void returnsMetadataForValidUrl() throws Exception {
    String url = "https://www.youtube.com/watch?v=abc";
    when(service.fetch(eq(url)))
        .thenReturn(
            new OembedMetadata(
                "youtube", "video", "T", "Author", "https://thumb", "<iframe/>", 320, 180));

    mvc.perform(get("/api/v1/public/oembed").param("url", url))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.provider").value("youtube"))
        .andExpect(jsonPath("$.type").value("video"))
        .andExpect(jsonPath("$.title").value("T"));
  }

  @Test
  void blankUrlReturns400NotServerError() throws Exception {
    // @NotBlank 위반 → ConstraintViolationException. 전용 핸들러가 생기기 전에는 catch-all 이 500 으로
    // 보고하던 자리 — 입력 오류이므로 400 VALIDATION_FAILED 여야 한다.
    mvc.perform(get("/api/v1/public/oembed").param("url", ""))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  void overlongUrlReturns400() throws Exception {
    String tooLong = "https://example.com/" + "a".repeat(3000);

    mvc.perform(get("/api/v1/public/oembed").param("url", tooLong))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }
}
