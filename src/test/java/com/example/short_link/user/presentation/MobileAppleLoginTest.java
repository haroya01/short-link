package com.example.short_link.user.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.user.application.dto.AppleIdentity;
import com.example.short_link.user.application.write.AppleIdentityVerifier;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import com.example.short_link.user.exception.UserErrorCode;
import com.example.short_link.user.exception.UserException;
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
class MobileAppleLoginTest {

  @Autowired private MockMvc mvc;
  @Autowired private UserRepository userRepository;
  @MockitoBean private AppleIdentityVerifier appleVerifier;

  private static final String BODY = "{\"identityToken\":\"t\",\"nonce\":\"n\"}";

  @Test
  void firstAppleLoginCreatesUserAndReturnsTokens() throws Exception {
    when(appleVerifier.verify(anyString(), anyString()))
        .thenReturn(new AppleIdentity("apple-new-1", "new@privaterelay.appleid.com"));

    mvc.perform(
            post("/api/v1/auth/mobile/apple").contentType(MediaType.APPLICATION_JSON).content(BODY))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").isString())
        .andExpect(jsonPath("$.refreshToken").isString());

    UserEntity created =
        userRepository.findByOauthProviderAndOauthId("apple", "apple-new-1").orElseThrow();
    assertThat(created.getEmail()).isEqualTo("new@privaterelay.appleid.com");
  }

  @Test
  void sameEmailLogsIntoExistingGoogleAccount() throws Exception {
    UserEntity existing =
        userRepository.save(new UserEntity("shared@example.com", "google", "g-link-1"));
    when(appleVerifier.verify(anyString(), anyString()))
        .thenReturn(new AppleIdentity("apple-link-1", "shared@example.com"));

    mvc.perform(
            post("/api/v1/auth/mobile/apple").contentType(MediaType.APPLICATION_JSON).content(BODY))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").isString());

    // No second row appears; the row keeps its original google identity.
    assertThat(userRepository.findByOauthProviderAndOauthId("apple", "apple-link-1")).isEmpty();
    assertThat(userRepository.findByEmail("shared@example.com").orElseThrow().getId())
        .isEqualTo(existing.getId());
  }

  @Test
  void invalidIdentityTokenReturns401() throws Exception {
    when(appleVerifier.verify(anyString(), anyString()))
        .thenThrow(new UserException(UserErrorCode.INVALID_APPLE_IDENTITY));

    mvc.perform(
            post("/api/v1/auth/mobile/apple").contentType(MediaType.APPLICATION_JSON).content(BODY))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_APPLE_IDENTITY"));
  }

  @Test
  void newAccountWithoutEmailReturns400() throws Exception {
    when(appleVerifier.verify(anyString(), anyString()))
        .thenReturn(new AppleIdentity("apple-no-email", null));

    mvc.perform(
            post("/api/v1/auth/mobile/apple").contentType(MediaType.APPLICATION_JSON).content(BODY))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("APPLE_EMAIL_REQUIRED"));
  }

  @Test
  void blankBodyFailsValidation() throws Exception {
    mvc.perform(
            post("/api/v1/auth/mobile/apple")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"identityToken\":\"\",\"nonce\":\"\"}"))
        .andExpect(status().isBadRequest());
  }
}
