package com.example.short_link.profile.visit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
class MyProfileStatsControllerTest {

  @Autowired private MockMvc mvc;
  @Autowired private JwtTokenService jwt;
  @Autowired private UserRepository userRepository;

  @Test
  void anonymousIs401() throws Exception {
    mvc.perform(get("/api/v1/users/me/profile/stats")).andExpect(status().isUnauthorized());
  }

  @Test
  void statsForOwner() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("s@x.com", "google", "g-mps"));
    String token = jwt.createAccessToken(user.getId(), "USER");

    mvc.perform(get("/api/v1/users/me/profile/stats").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());
  }

  @Test
  void summaryForOwner() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("u@x.com", "google", "g-mpu"));
    String token = jwt.createAccessToken(user.getId(), "USER");

    mvc.perform(
            get("/api/v1/users/me/profile/stats/summary")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.allTime").isNumber());
  }

  @Test
  void getVisibilityDefaultsFalse() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("vd@x.com", "google", "g-mpvd"));
    String token = jwt.createAccessToken(user.getId(), "USER");

    mvc.perform(
            get("/api/v1/users/me/profile/stats/visibility")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.isPublic").value(false));
  }

  @Test
  void patchVisibilityFlipsToTrue() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("p@x.com", "google", "g-mpvp"));
    String token = jwt.createAccessToken(user.getId(), "USER");

    mvc.perform(
            patch("/api/v1/users/me/profile/stats/visibility")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"isPublic\":true}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.isPublic").value(true));
  }
}
