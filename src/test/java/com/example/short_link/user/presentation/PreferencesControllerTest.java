package com.example.short_link.user.presentation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.user.application.JwtTokenService;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.UserRepository;
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
class PreferencesControllerTest {

  @Autowired private MockMvc mvc;
  @Autowired private UserRepository userRepository;
  @Autowired private JwtTokenService jwt;

  @Test
  void updatesTimezoneForCurrentUser() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("t@x.com", "google", "g-tz"));
    String token = jwt.createAccessToken(user.getId());

    mvc.perform(
            put("/api/v1/users/me/preferences")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"timezone\":\"America/New_York\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.timezone").value("America/New_York"));
  }

  @Test
  void rejectsInvalidTimezone() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("t@x.com", "google", "g-tz2"));
    String token = jwt.createAccessToken(user.getId());

    mvc.perform(
            put("/api/v1/users/me/preferences")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"timezone\":\"Mars/Olympus\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_TIMEZONE"));
  }

  @Test
  void rejectsBlankTimezone() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("t@x.com", "google", "g-tz3"));
    String token = jwt.createAccessToken(user.getId());

    mvc.perform(
            put("/api/v1/users/me/preferences")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"timezone\":\"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  void rejectsAnonymous() throws Exception {
    mvc.perform(
            put("/api/v1/users/me/preferences")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"timezone\":\"Asia/Seoul\"}"))
        .andExpect(status().isUnauthorized());
  }
}
