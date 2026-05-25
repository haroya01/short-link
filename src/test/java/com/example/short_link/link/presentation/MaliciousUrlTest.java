package com.example.short_link.link.presentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.link.safety.application.UrlSafetyChecker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class MaliciousUrlTest {

  @Autowired private MockMvc mvc;
  @MockitoBean private UrlSafetyChecker urlSafetyChecker;

  @Test
  void rejectsMaliciousUrlWithCode() throws Exception {
    when(urlSafetyChecker.isSafe(any())).thenReturn(false);

    mvc.perform(
            post("/api/v1/links")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"https://malware.test/exploit\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("MALICIOUS_URL"));
  }

  @Test
  void allowsSafeUrl() throws Exception {
    when(urlSafetyChecker.isSafe(any())).thenReturn(true);

    mvc.perform(
            post("/api/v1/links")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"https://safe.example/path\"}"))
        .andExpect(status().isCreated());
  }
}
