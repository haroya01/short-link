package com.example.short_link;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.common.exception.GuardUnavailableException;
import com.example.short_link.common.pow.PowService;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.stats.application.ClickBuffer;
import com.example.short_link.post.application.access.PostPasswordAttempts;
import com.example.short_link.support.FaultyRedis;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RedisOutageHttpIntegrationTest {

  private static final FaultyRedis REDIS = FaultyRedis.inFrontOfTheTestRedis();
  private static final Duration A_FEW_COMMANDS = Duration.ofSeconds(10);

  @DynamicPropertySource
  static void throughTheFaultyLine(DynamicPropertyRegistry registry) {
    REDIS.route(registry);
  }

  @Autowired private MockMvc mvc;
  @Autowired private LinkRepository linkRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private StringRedisTemplate redis;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ClickBuffer clickBuffer;
  @Autowired private PowService pow;
  @Autowired private PostPasswordAttempts postPasswordAttempts;

  private Long linkId;

  @AfterEach
  void healAndForget() {
    REDIS.heal();
    assertTimeoutPreemptively(Duration.ofSeconds(40), this::waitUntilRedisAnswers);
    clickBuffer.drain(Integer.MAX_VALUE);
    if (linkId != null) {
      jdbc.update("DELETE FROM click_event WHERE link_id = ?", linkId);
      jdbc.update("DELETE FROM link WHERE id = ?", linkId);
    }
  }

  @Test
  void aShortLinkStillRedirectsWhileRedisIsDown() throws Exception {
    String code = link("https://example.com/landing");
    REDIS.drop();

    assertTimeoutPreemptively(
        A_FEW_COMMANDS,
        () ->
            mvc.perform(get("/" + code))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/landing")));
  }

  @Test
  void aPublicProfileStillReadsWhileRedisIsDown() throws Exception {
    String handle = "ro" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    UserEntity user = new UserEntity(handle + "@x.com", "google", handle);
    user.claimUsername(handle);
    userRepository.save(user);
    REDIS.drop();

    assertTimeoutPreemptively(
        A_FEW_COMMANDS,
        () ->
            mvc.perform(get("/api/v1/public/profiles/" + handle))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(handle)));
  }

  @Test
  void aTwoFactorGuessIsTurnedAwayWithRetryAfterWhileRedisIsDown() throws Exception {
    REDIS.drop();

    assertTimeoutPreemptively(
        A_FEW_COMMANDS,
        () ->
            mvc.perform(
                    post("/api/v1/auth/2fa/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"challenge\":\"c\",\"code\":\"123456\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "30"))
                .andExpect(jsonPath("$.code").value("TEMPORARILY_UNAVAILABLE")));
  }

  @Test
  void aLinkPasswordGuessIsTurnedAwayWithRetryAfterWhileRedisIsDown() throws Exception {
    String code = link("https://example.com/secret");
    jdbc.update(
        "UPDATE link SET password_hash = ? WHERE id = ?",
        "$2a$10$abcdefghijklmnopqrstuuJ9rN2Qyq2E5xJQ6cZ0m4m3q1y8e3w8a",
        linkId);
    REDIS.drop();

    assertTimeoutPreemptively(
        A_FEW_COMMANDS,
        () ->
            mvc.perform(
                    post("/" + code)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content("password=guess"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "30"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML)));
  }

  @Test
  void aPostPasswordGuessIsTurnedAwayWhileRedisIsDown() {
    REDIS.drop();

    assertTimeoutPreemptively(
        A_FEW_COMMANDS,
        () ->
            assertThatThrownBy(() -> postPasswordAttempts.tryAttempt(1L, "203.0.113.7"))
                .isInstanceOf(GuardUnavailableException.class));
  }

  @Test
  void aProofOfWorkChallengeIsTurnedAwayWithRetryAfterWhileRedisIsDown() throws Exception {
    REDIS.drop();

    assertTimeoutPreemptively(
        A_FEW_COMMANDS,
        () ->
            mvc.perform(get("/api/v1/pow/challenge"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "30"))
                .andExpect(jsonPath("$.code").value("TEMPORARILY_UNAVAILABLE")));
  }

  @Test
  void aSolvedProofOfWorkIsNotTakenOnTrustWhileRedisIsDown() throws Exception {
    PowService.Challenge issued = pow.issue();
    String nonce = solve(issued);
    REDIS.drop();

    assertTimeoutPreemptively(
        A_FEW_COMMANDS,
        () ->
            assertThatThrownBy(() -> pow.verifyAndConsume(issued.challenge(), nonce))
                .isInstanceOf(GuardUnavailableException.class));
  }

  private String link(String url) {
    String code = "ro" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    LinkEntity saved =
        linkRepository.save(new LinkEntity(url, code, null, Instant.now().plusSeconds(3600)));
    linkId = saved.getId();
    return code;
  }

  private static String solve(PowService.Challenge issued) throws Exception {
    MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
    String zeros = "0".repeat(issued.difficulty());
    for (long nonce = 0; ; nonce++) {
      String candidate = Long.toString(nonce);
      byte[] digest =
          sha256.digest((issued.challenge() + ":" + candidate).getBytes(StandardCharsets.UTF_8));
      if (HexFormat.of().formatHex(digest).startsWith(zeros)) {
        return candidate;
      }
    }
  }

  private void waitUntilRedisAnswers() throws InterruptedException {
    while (true) {
      try {
        redis.opsForValue().get("redis-outage-http-test:" + UUID.randomUUID());
        return;
      } catch (DataAccessException stillDown) {
        Thread.sleep(200);
      }
    }
  }
}
