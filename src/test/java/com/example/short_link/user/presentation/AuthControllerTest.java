package com.example.short_link.user.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.user.application.JwtTokenService;
import com.example.short_link.user.application.write.RefreshTokenStore;
import com.example.short_link.user.domain.RefreshToken;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthControllerTest {

  @Autowired private MockMvc mvc;
  @Autowired private JwtTokenService jwt;
  @Autowired private RefreshTokenStore refreshStore;
  @Autowired private UserRepository userRepository;

  @Test
  void refreshIssuesNewAccessToken() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("u@example.com", "google", "g-u"));
    RefreshToken refresh = jwt.createRefreshToken(user.getId());
    refreshStore.save(user.getId(), refresh.jti(), Duration.ofDays(14));

    mvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie("refresh_token", refresh.token())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").isString());
  }

  @Test
  void refreshWithoutCookieReturns401() throws Exception {
    mvc.perform(post("/api/v1/auth/refresh"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
  }

  @Test
  void refreshWithInvalidTokenReturns401() throws Exception {
    mvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie("refresh_token", "not-a-jwt")))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void refreshReplayWithinGraceSucceeds() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("u@example.com", "google", "g-u"));
    RefreshToken refresh = jwt.createRefreshToken(user.getId());
    refreshStore.save(user.getId(), refresh.jti(), Duration.ofDays(14));

    mvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie("refresh_token", refresh.token())))
        .andExpect(status().isOk());

    // Replaying the just-rotated token within the grace window is the benign cross-tab/subdomain
    // race, so it re-issues a fresh pair instead of returning 401.
    mvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie("refresh_token", refresh.token())))
        .andExpect(status().isOk());
  }

  @Test
  void refreshWithUnknownTokenReturns401() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("u@example.com", "google", "g-u"));
    // Never stored and never just rotated → treated as reuse/theft.
    RefreshToken stolen = jwt.createRefreshToken(user.getId());

    mvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie("refresh_token", stolen.token())))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void logoutClearsRefreshFromStore() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("u@example.com", "google", "g-u"));
    String access = jwt.createAccessToken(user.getId(), "USER");
    RefreshToken refresh = jwt.createRefreshToken(user.getId());
    refreshStore.save(user.getId(), refresh.jti(), Duration.ofDays(14));

    mvc.perform(
            post("/api/v1/auth/logout")
                .header("Authorization", "Bearer " + access)
                .cookie(new Cookie("refresh_token", refresh.token())))
        .andExpect(status().isOk());

    assertThat(refreshStore.exists(user.getId(), refresh.jti())).isFalse();
  }

  @Test
  void logoutWithoutAuthReturns401() throws Exception {
    mvc.perform(post("/api/v1/auth/logout")).andExpect(status().isUnauthorized());
  }
}
