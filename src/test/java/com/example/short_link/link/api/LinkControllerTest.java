package com.example.short_link.link.api;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", startsWith("http://localhost:8080/")))
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

  @Test
  void rejectsTooLongUrl() throws Exception {
    String longUrl = "https://example.com/" + "a".repeat(3000);
    mvc.perform(
            post("/api/v1/links")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"" + longUrl + "\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void rejectsJavascriptScheme() throws Exception {
    mvc.perform(
            post("/api/v1/links")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"javascript:alert(1)\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void rejectsDataScheme() throws Exception {
    mvc.perform(
            post("/api/v1/links")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"data:text/html,<script>alert(1)</script>\"}"))
        .andExpect(status().isBadRequest());
  }
}
