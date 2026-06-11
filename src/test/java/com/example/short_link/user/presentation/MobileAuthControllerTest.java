package com.example.short_link.user.presentation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.user.application.JwtTokenService;
import com.example.short_link.user.application.write.MobileExchangeCodeStore;
import com.example.short_link.user.application.write.RefreshTokenStore;
import com.example.short_link.user.domain.RefreshToken;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.time.Duration;
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
class MobileAuthControllerTest {

  @Autowired private MockMvc mvc;
  @Autowired private JwtTokenService jwt;
  @Autowired private RefreshTokenStore refreshStore;
  @Autowired private MobileExchangeCodeStore exchangeCodes;
  @Autowired private UserRepository userRepository;

  @Test
  void startRedirectsIntoOAuthDance() throws Exception {
    mvc.perform(get("/api/v1/auth/mobile/start"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/oauth2/authorization/google"));
  }

  @Test
  void exchangeReturnsTokenPairInBody() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("m@example.com", "google", "g-m1"));
    String code = exchangeCodes.create(user.getId());

    mvc.perform(
            post("/api/v1/auth/mobile/exchange")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + code + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").isString())
        .andExpect(jsonPath("$.refreshToken").isString());
  }

  @Test
  void exchangeCodeIsSingleUse() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("m@example.com", "google", "g-m2"));
    String code = exchangeCodes.create(user.getId());
    String body = "{\"code\":\"" + code + "\"}";

    mvc.perform(
            post("/api/v1/auth/mobile/exchange")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk());

    mvc.perform(
            post("/api/v1/auth/mobile/exchange")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_EXCHANGE_CODE"));
  }

  @Test
  void exchangeWithUnknownCodeReturns401() throws Exception {
    mvc.perform(
            post("/api/v1/auth/mobile/exchange")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"never-issued\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void refreshAcceptsTokenInBody() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("m@example.com", "google", "g-m3"));
    RefreshToken refresh = jwt.createRefreshToken(user.getId());
    refreshStore.save(user.getId(), refresh.jti(), Duration.ofDays(14));

    mvc.perform(
            post("/api/v1/auth/mobile/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refresh.token() + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").isString())
        .andExpect(jsonPath("$.refreshToken").isString());
  }

  @Test
  void refreshWithGarbageTokenReturns401() throws Exception {
    mvc.perform(
            post("/api/v1/auth/mobile/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"not-a-jwt\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void logoutKillsExactlyThePresentedSession() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("m@example.com", "google", "g-m4"));
    RefreshToken refresh = jwt.createRefreshToken(user.getId());
    refreshStore.save(user.getId(), refresh.jti(), Duration.ofDays(14));
    String body = "{\"refreshToken\":\"" + refresh.token() + "\"}";

    mvc.perform(
            post("/api/v1/auth/mobile/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isNoContent());

    mvc.perform(
            post("/api/v1/auth/mobile/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isUnauthorized());
  }
}
