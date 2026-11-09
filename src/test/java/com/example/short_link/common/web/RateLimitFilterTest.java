package com.example.short_link.common.web;

import static com.example.short_link.support.TestCacheCleaner.clear;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.user.application.JwtTokenService;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.net.URI;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

// application-test.yml 의 1,000,000 큰 한도 (빌드 중 anonymous 누적 회피용) 와 별개로,
// 이 테스트만 운영 한도 (anonymous=100, authenticated=1000) 를 고정해 임계 동작을 검증한다.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@TestPropertySource(
    properties = {
      "short-link.rate-limit.anonymous=100",
      "short-link.rate-limit.authenticated=1000"
    })
class RateLimitFilterTest {

  @Autowired private MockMvc mvc;
  @Autowired private StringRedisTemplate redis;
  @Autowired private JwtTokenService jwt;
  @Autowired private UserRepository userRepository;
  @Autowired private CacheManager cacheManager;

  @BeforeEach
  void resetState() {
    clear(cacheManager, "link");
    cleanup();
  }

  @AfterEach
  void cleanup() {
    Set<String> keys = redis.keys("rate:*");
    if (keys != null && !keys.isEmpty()) {
      redis.delete(keys);
    }
  }

  @Test
  void blocksAnonymousAfterLimit() throws Exception {
    redis.opsForValue().set("rate:ip:127.0.0.1", "100", Duration.ofMinutes(1));

    mvc.perform(get("/abc1234"))
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
        .andExpect(header().string("Retry-After", "60"));
  }

  @Test
  void allowsAnonymousBelowLimit() throws Exception {
    redis.opsForValue().set("rate:ip:127.0.0.1", "50", Duration.ofMinutes(1));

    mvc.perform(get("/abc1234")).andExpect(status().isNotFound());
  }

  @Test
  void blocksAuthenticatedUserAfterLimit() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("u@example.com", "google", "g-rl"));
    String token = jwt.createAccessToken(user.getId(), "USER");
    redis.opsForValue().set("rate:user:" + user.getId(), "1000", Duration.ofMinutes(1));

    mvc.perform(get("/api/v1/links/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isTooManyRequests());
  }

  @Test
  void distinguishesIpAndUserBuckets() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("u@example.com", "google", "g-rl2"));
    String token = jwt.createAccessToken(user.getId(), "USER");
    redis.opsForValue().set("rate:ip:127.0.0.1", "100", Duration.ofMinutes(1));

    mvc.perform(get("/api/v1/links/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());
  }

  @Test
  void blocksTwoFactorVerifyAfterPerEndpointLimit() throws Exception {
    redis
        .opsForValue()
        .set("rate:ep:POST:/api/v1/auth/2fa/verify:ip:127.0.0.1", "5", Duration.ofMinutes(1));

    mvc.perform(
            post("/api/v1/auth/2fa/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"challengeToken\":\"x\",\"code\":\"000000\"}"))
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
  }

  @Test
  void blocksRefreshAfterTenAttempts() throws Exception {
    redis
        .opsForValue()
        .set("rate:ep:POST:/api/v1/auth/refresh:ip:127.0.0.1", "10", Duration.ofMinutes(1));

    mvc.perform(post("/api/v1/auth/refresh"))
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
  }

  @Test
  void blocksLinkReportsAfterTenFromOneAddress() throws Exception {
    redis
        .opsForValue()
        .set(
            "rate:ep:POST:/api/v1/public/abuse-reports/links:ip:127.0.0.1",
            "10",
            Duration.ofMinutes(1));

    mvc.perform(
            post("/api/v1/public/abuse-reports/links")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"link\":\"kurl.me/abc123\",\"reasonCode\":\"PHISHING\"}"))
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
  }

  @Test
  void appTwoFactorVerifyHasTheSameLimitAsTheWebOne() throws Exception {
    for (int i = 0; i < 5; i++) {
      mvc.perform(mobileTwoFactorVerify()).andExpect(notRateLimited());
    }

    mvc.perform(mobileTwoFactorVerify()).andExpect(status().isTooManyRequests());
  }

  @Test
  void anAttackersOwnTokenDoesNotLiftTheTwoFactorLimit() throws Exception {
    UserEntity attacker = userRepository.save(new UserEntity("a@example.com", "google", "g-rl3"));
    String token = jwt.createAccessToken(attacker.getId(), "USER");
    for (int i = 0; i < 5; i++) {
      mvc.perform(mobileTwoFactorVerify().header("Authorization", "Bearer " + token))
          .andExpect(notRateLimited());
    }

    mvc.perform(mobileTwoFactorVerify().header("Authorization", "Bearer " + token))
        .andExpect(status().isTooManyRequests());
  }

  @Test
  void regeneratingRecoveryCodesHasTheSameLimitAsDisablingTwoFactor() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("r@example.com", "google", "g-rl4"));
    String token = jwt.createAccessToken(user.getId(), "USER");
    for (int i = 0; i < 5; i++) {
      mvc.perform(regenerate(token)).andExpect(notRateLimited());
    }

    mvc.perform(regenerate(token)).andExpect(status().isTooManyRequests());
  }

  @Test
  void anEncodedLetterInThePathStillCountsAgainstTheEndpointLimit() throws Exception {
    for (int i = 0; i < 5; i++) {
      mvc.perform(webTwoFactorVerify("/api/v1/auth/2fa/verify")).andExpect(notRateLimited());
    }

    mvc.perform(webTwoFactorVerify("/api/v1/auth/2fa/verif%79"))
        .andExpect(status().isTooManyRequests());
  }

  @Test
  void addressesInOneIpv6SlashSixtyFourShareTheEndpointLimit() throws Exception {
    for (int i = 1; i <= 5; i++) {
      String address = "2001:db8:1:2::" + i;
      mvc.perform(webTwoFactorVerify("/api/v1/auth/2fa/verify").with(from(address)))
          .andExpect(notRateLimited());
    }

    mvc.perform(webTwoFactorVerify("/api/v1/auth/2fa/verify").with(from("2001:db8:1:2::ff")))
        .andExpect(status().isTooManyRequests());
    mvc.perform(webTwoFactorVerify("/api/v1/auth/2fa/verify").with(from("2001:db8:1:3::1")))
        .andExpect(notRateLimited());
  }

  private static MockHttpServletRequestBuilder mobileTwoFactorVerify() {
    return post("/api/v1/auth/mobile/2fa/verify")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"challenge\":\"x\",\"code\":\"000000\"}");
  }

  private static MockHttpServletRequestBuilder webTwoFactorVerify(String path) {
    return post(URI.create(path))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"challenge\":\"x\",\"code\":\"000000\"}");
  }

  private static MockHttpServletRequestBuilder regenerate(String token) {
    return post("/api/v1/2fa/recovery-codes/regenerate")
        .header("Authorization", "Bearer " + token)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"code\":\"000000\"}");
  }

  private static ResultMatcher notRateLimited() {
    return result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(429);
  }

  private static RequestPostProcessor from(String address) {
    return request -> {
      request.setRemoteAddr(address);
      return request;
    };
  }

  // Sanity: per-endpoint bucket below limit does not block (the endpoint will reject for its own
  // reasons — empty body, bad token — but not with 429).
  @Test
  void allowsTwoFactorVerifyBelowPerEndpointLimit() throws Exception {
    redis
        .opsForValue()
        .set("rate:ep:POST:/api/v1/auth/2fa/verify:ip:127.0.0.1", "3", Duration.ofMinutes(1));

    mvc.perform(
            post("/api/v1/auth/2fa/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"challengeToken\":\"x\",\"code\":\"000000\"}"))
        .andExpect(
            result -> {
              int s = result.getResponse().getStatus();
              if (s == 429) {
                throw new AssertionError(
                    "expected non-429 (per-endpoint bucket below limit), got 429");
              }
            });
  }
}
