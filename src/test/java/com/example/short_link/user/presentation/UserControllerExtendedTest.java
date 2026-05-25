package com.example.short_link.user.presentation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.user.application.JwtTokenService;
import com.example.short_link.user.domain.UserEntity;
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
class UserControllerExtendedTest {

  @Autowired private MockMvc mvc;
  @Autowired private JwtTokenService jwt;
  @Autowired private UserRepository userRepository;

  @Test
  void updatePreferencesAnonymousIs401() throws Exception {
    mvc.perform(
            put("/api/v1/users/me/preferences")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"timezone\":\"Asia/Seoul\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void updatePreferencesValidTimezone() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("p@x.com", "google", "g-pre"));
    String token = jwt.createAccessToken(user.getId(), "USER");

    mvc.perform(
            put("/api/v1/users/me/preferences")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"timezone\":\"Asia/Tokyo\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.timezone").value("Asia/Tokyo"));
  }

  @Test
  void updatePreferencesInvalidTimezoneRejected() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("p@x.com", "google", "g-pri"));
    String token = jwt.createAccessToken(user.getId(), "USER");

    mvc.perform(
            put("/api/v1/users/me/preferences")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"timezone\":\"Mars/Olympus\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void exportReturnsJsonAttachment() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("e@x.com", "google", "g-exp"));
    String token = jwt.createAccessToken(user.getId(), "USER");

    mvc.perform(get("/api/v1/users/me/export").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Type", "application/json"))
        .andExpect(
            header()
                .string(
                    "Content-Disposition",
                    org.hamcrest.Matchers.containsString(
                        "attachment; filename=\"kurl-export-" + user.getId())));
  }

  @Test
  void exportAnonymousIs401() throws Exception {
    mvc.perform(get("/api/v1/users/me/export")).andExpect(status().isUnauthorized());
  }

  @Test
  void deleteMeReturns204() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("d@x.com", "google", "g-del"));
    String token = jwt.createAccessToken(user.getId(), "USER");

    mvc.perform(delete("/api/v1/users/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isNoContent());
  }

  @Test
  void deleteMeAnonymousIs401() throws Exception {
    mvc.perform(delete("/api/v1/users/me")).andExpect(status().isUnauthorized());
  }
}
