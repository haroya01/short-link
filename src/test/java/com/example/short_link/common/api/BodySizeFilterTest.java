package com.example.short_link.common.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BodySizeFilterTest {

  @Autowired private MockMvc mvc;

  @Test
  void smallBodyPassesThrough() throws Exception {
    mvc.perform(
            post("/api/v1/links")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"https://example.com\"}"))
        .andExpect(status().isCreated());
  }

  @Test
  void bodyExceeding16KbReturns413WithProblemDetail() throws Exception {
    String oversize = "a".repeat(17 * 1024);
    String body = "{\"url\":\"https://example.com\",\"note\":\"" + oversize + "\"}";

    mvc.perform(post("/api/v1/links").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isPayloadTooLarge())
        .andExpect(content().contentType("application/problem+json"))
        .andExpect(jsonPath("$.code").value("PAYLOAD_TOO_LARGE"))
        .andExpect(jsonPath("$.detail").value("request body exceeds 16KB limit"));
  }
}
