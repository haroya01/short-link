package com.example.short_link.user.presentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.testsupport.KurlWebMvcTest;
import com.example.short_link.user.application.dto.IssuedTokens;
import com.example.short_link.user.application.write.AuthService;
import com.example.short_link.user.application.write.AuthService.LoginResult;
import com.example.short_link.user.presentation.helper.RefreshCookieWriter;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@KurlWebMvcTest(controllers = DevAuthController.class)
@ActiveProfiles("test")
class DevAuthControllerTest {

  @Autowired private MockMvc mvc;

  @MockitoBean private AuthService authService;
  @MockitoBean private RefreshCookieWriter refreshCookieWriter;

  @Test
  void issuesTokensAndRefreshCookie() throws Exception {
    when(authService.loginWithOAuth("dev@local.test", "dev", "dev:dev@local.test"))
        .thenReturn(new LoginResult.Tokens(new IssuedTokens("access-token", "refresh-token")));
    doAnswer(
            invocation -> {
              HttpServletResponse response = invocation.getArgument(0);
              response.addHeader("Set-Cookie", "refresh_token=refresh-token; Path=/api/v1/auth");
              return null;
            })
        .when(refreshCookieWriter)
        .set(any(HttpServletResponse.class), eq("refresh-token"));

    mvc.perform(
            post("/api/v1/auth/dev-login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"dev@local.test\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").value("access-token"))
        .andExpect(cookie().exists("refresh_token"));

    verify(refreshCookieWriter).set(any(HttpServletResponse.class), eq("refresh-token"));
  }

  @Test
  void delegatesToDevOauthIdentity() throws Exception {
    when(authService.loginWithOAuth("dev2@local.test", "dev", "dev:dev2@local.test"))
        .thenReturn(new LoginResult.Tokens(new IssuedTokens("access-token", "refresh-token")));

    mvc.perform(
            post("/api/v1/auth/dev-login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"dev2@local.test\"}"))
        .andExpect(status().isOk());

    verify(authService).loginWithOAuth("dev2@local.test", "dev", "dev:dev2@local.test");
  }

  @Test
  void twoFactorChallengeReturnsAccepted() throws Exception {
    when(authService.loginWithOAuth("mfa@local.test", "dev", "dev:mfa@local.test"))
        .thenReturn(new LoginResult.TwoFactorRequired("challenge-token"));

    mvc.perform(
            post("/api/v1/auth/dev-login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"mfa@local.test\"}"))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.challenge").value("challenge-token"));
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
