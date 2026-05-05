package com.example.short_link.link;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class LinkControllerTest {

  @Autowired private MockMvc mvc;

  @Test
  void createsShortLink() throws Exception {
    mvc.perform(
            post("/api/v1/links")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"https://example.com/path\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.shortCode").isString())
        .andExpect(jsonPath("$.shortUrl").isString());
  }

  @Test
  void rejectsBlankUrl() throws Exception {
    mvc.perform(
            post("/api/v1/links").contentType(MediaType.APPLICATION_JSON).content("{\"url\":\"\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void rejectsInvalidUrl() throws Exception {
    mvc.perform(
            post("/api/v1/links")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"not-a-valid-url\"}"))
        .andExpect(status().isBadRequest());
  }
}
