package com.example.short_link.user.presentation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.user.domain.repository.UserRepository;
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
class DevAuthControllerTest {

  @Autowired private MockMvc mvc;
  @Autowired private UserRepository userRepository;

  @Test
  void issuesTokensAndCreatesUserOnFirstCall() throws Exception {
    long before = userRepository.count();
    mvc.perform(
            post("/api/v1/auth/dev-login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"dev@local.test\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").isString())
        .andExpect(cookie().exists("refresh_token"));
    long after = userRepository.count();
    org.assertj.core.api.Assertions.assertThat(after).isEqualTo(before + 1);
  }

  @Test
  void reusesExistingDevUser() throws Exception {
    mvc.perform(
            post("/api/v1/auth/dev-login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"dev2@local.test\"}"))
        .andExpect(status().isOk());
    long beforeSecond = userRepository.count();
    mvc.perform(
            post("/api/v1/auth/dev-login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"dev2@local.test\"}"))
        .andExpect(status().isOk());
    org.assertj.core.api.Assertions.assertThat(userRepository.count()).isEqualTo(beforeSecond);
  }

  @Test
  void rejectsInvalidEmail() throws Exception {
    mvc.perform(
            post("/api/v1/auth/dev-login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"not-an-email\"}"))
        .andExpect(status().isBadRequest());
  }
}
