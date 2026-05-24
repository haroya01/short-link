package com.example.short_link.common.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.user.application.JwtTokenService;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
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
    String token = jwt.createAccessToken(user.getId());
    redis.opsForValue().set("rate:user:" + user.getId(), "1000", Duration.ofMinutes(1));

    mvc.perform(get("/api/v1/links/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isTooManyRequests());
  }

  @Test
  void distinguishesIpAndUserBuckets() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("u@example.com", "google", "g-rl2"));
    String token = jwt.createAccessToken(user.getId());
    redis.opsForValue().set("rate:ip:127.0.0.1", "100", Duration.ofMinutes(1));

    mvc.perform(get("/api/v1/links/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());
  }
}
