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
}
