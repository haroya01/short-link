package com.example.short_link.common.pow;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class PowServiceTest {

  @Autowired private StringRedisTemplate redis;
  private SimpleMeterRegistry metrics;
  private PowService service;

  @BeforeEach
  void useRealRedisWithAnInexpensiveProof() {
    metrics = new SimpleMeterRegistry();
    service =
        new PowService(
            new PowProof(),
            new PowChallengeStore(redis),
            new PowMetrics(metrics),
            new PowProperties(2, true));
  }

  @Test
  void verifyAcceptsCorrectProofAndConsumesChallenge() throws Exception {
    PowService.Challenge challenge = service.issue();
    String key = "pow:challenge:" + challenge.challenge();
    assertThat(redis.getExpire(key)).isBetween(1L, 300L);
    String nonce = mineProof(challenge.challenge(), 2);

    assertThat(service.verifyAndConsume(challenge.challenge(), nonce)).isTrue();
    assertThat(redis.hasKey(key)).isFalse();
    assertThat(service.verifyAndConsume(challenge.challenge(), nonce)).isFalse();
    assertThat(metrics.get("pow.challenge.issued").counter().count()).isEqualTo(1);
    assertThat(metrics.get("pow.verify").tag("result", "ok").counter().count()).isEqualTo(1);
    assertThat(metrics.get("pow.verify").tag("result", "unknown_or_used").counter().count())
        .isEqualTo(1);
  }

  @Test
  void invalidProofLeavesIssuedChallengeAvailableForItsCorrectProof() throws Exception {
    PowService.Challenge challenge = service.issue();
    String wrongNonce = findInvalidNonce(challenge.challenge());

    assertThat(service.verifyAndConsume(challenge.challenge(), wrongNonce)).isFalse();
    assertThat(redis.hasKey("pow:challenge:" + challenge.challenge())).isTrue();
    assertThat(service.verifyAndConsume(challenge.challenge(), mineProof(challenge.challenge(), 2)))
        .isTrue();
  }

  @Test
  void validProofForAnUnknownChallengeIsRejected() {
    // Independent SHA-256 vector: SHA-256("deadbeefcafe:102") starts with 009e9d.
    assertThat(service.verifyAndConsume("deadbeefcafe", "102")).isFalse();
    assertThat(metrics.get("pow.verify").tag("result", "unknown_or_used").counter().count())
        .isEqualTo(1);
  }

  @Test
  void concurrentPresentationsOfOneProofHaveExactlyOneWinner() throws Exception {
    PowService.Challenge challenge = service.issue();
    String nonce = mineProof(challenge.challenge(), 2);
    CountDownLatch start = new CountDownLatch(1);
    Callable<Boolean> presentProof =
        () -> {
          start.await();
          return service.verifyAndConsume(challenge.challenge(), nonce);
        };

    try (var callers = Executors.newVirtualThreadPerTaskExecutor()) {
      var first = callers.submit(presentProof);
      var second = callers.submit(presentProof);
      start.countDown();
      assertThat(List.of(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(true, false);
    }
    assertThat(redis.hasKey("pow:challenge:" + challenge.challenge())).isFalse();
    assertThat(metrics.get("pow.verify").tag("result", "ok").counter().count()).isEqualTo(1);
  }

  private static String findInvalidNonce(String challenge) throws Exception {
    for (int candidate = 0; candidate < 1_000_000; candidate++) {
      String nonce = Integer.toString(candidate);
      if (!clientHash(challenge, nonce).startsWith("00")) return nonce;
    }
    throw new IllegalStateException("could not find invalid proof");
  }

  private static String mineProof(String challenge, int difficulty) throws Exception {
    String requiredPrefix = "0".repeat(difficulty);
    for (int candidate = 0; candidate < 1_000_000; candidate++) {
      String nonce = Integer.toString(candidate);
      if (clientHash(challenge, nonce).startsWith(requiredPrefix)) return nonce;
    }
    throw new IllegalStateException("could not mine proof");
  }

  private static String clientHash(String challenge, String nonce) throws Exception {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    byte[] input = (challenge + ":" + nonce).getBytes(StandardCharsets.UTF_8);
    return HexFormat.of().formatHex(digest.digest(input));
  }
}
