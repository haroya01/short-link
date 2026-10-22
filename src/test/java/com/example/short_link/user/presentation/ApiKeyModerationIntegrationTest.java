package com.example.short_link.user.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.common.user.UserModerationPort;
import com.example.short_link.user.application.JwtTokenService;
import com.example.short_link.user.application.write.ApiKeyService;
import com.example.short_link.user.application.write.RefreshTokenStore;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.ApiKeyRepository;
import com.example.short_link.user.domain.repository.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
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
class ApiKeyModerationIntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private UserRepository userRepository;
  @Autowired private ApiKeyRepository apiKeyRepository;
  @Autowired private ApiKeyService apiKeys;
  @Autowired private UserModerationPort moderation;
  @Autowired private RefreshTokenStore refreshTokens;
  @Autowired private JwtTokenService jwt;

  @Test
  void aBannedAccountsKeyNoLongerAuthenticates() throws Exception {
    UserEntity user = newUser();
    String rawKey = apiKeys.issue(user.getId(), "before-ban").rawKey();
    mvc.perform(get("/api/v1/users/me").header("X-API-Key", rawKey)).andExpect(status().isOk());

    user.ban();
    userRepository.save(user);

    mvc.perform(get("/api/v1/users/me").header("X-API-Key", rawKey))
        .andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + rawKey))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void aBannedOrSuspendedAccountCannotIssueAKey() throws Exception {
    UserEntity banned = newUser();
    banned.ban();
    userRepository.save(banned);
    UserEntity suspended = newUser();
    suspended.suspend(Instant.now().plus(Duration.ofDays(1)));
    userRepository.save(suspended);

    for (UserEntity user : new UserEntity[] {banned, suspended}) {
      mvc.perform(
              post("/api/v1/users/me/api-keys")
                  .header("Authorization", "Bearer " + jwt.createAccessToken(user.getId(), "USER"))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"name\":\"after-ban\"}"))
          .andExpect(status().isForbidden());
      assertThat(apiKeyRepository.findAllByUserIdOrderByCreatedAtDesc(user.getId())).isEmpty();
    }
  }

  @Test
  void banningRevokesEveryKeyAndEndsEverySession() {
    UserEntity user = newUser();
    UserEntity bystander = newUser();
    String first = apiKeys.issue(user.getId(), "one").rawKey();
    String second = apiKeys.issue(user.getId(), "two").rawKey();
    String othersKey = apiKeys.issue(bystander.getId(), "other").rawKey();
    String jti = UUID.randomUUID().toString();
    refreshTokens.save(user.getId(), jti, Duration.ofDays(1));

    moderation.ban(1L, user.getId());

    assertThat(apiKeys.resolve(first)).isEmpty();
    assertThat(apiKeys.resolve(second)).isEmpty();
    assertThat(apiKeys.resolve(othersKey)).isPresent();
    assertThat(refreshTokens.exists(user.getId(), jti)).isFalse();
  }

  private UserEntity newUser() {
    String tag = UUID.randomUUID().toString();
    return userRepository.save(new UserEntity(tag + "@x.com", "google", tag));
  }
}
