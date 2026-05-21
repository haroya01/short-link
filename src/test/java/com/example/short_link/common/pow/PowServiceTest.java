package com.example.short_link.common.pow;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.queryaudit.junit5.QueryAudit;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@QueryAudit
class PowServiceTest {

  @Autowired private StringRedisTemplate redis;

  @Test
  void verifyAcceptsCorrectProofAndConsumesChallenge() {
    PowService service = new PowService(redis, new SimpleMeterRegistry(), 2, true);
    PowService.Challenge challenge = service.issue();

    String nonce = mineProof(challenge.challenge(), 2);
    assertThat(service.verifyAndConsume(challenge.challenge(), nonce)).isTrue();
    // single-use: same proof can't be replayed
    assertThat(service.verifyAndConsume(challenge.challenge(), nonce)).isFalse();
  }

  @Test
  void verifyRejectsWrongNonce() {
    PowService service = new PowService(redis, new SimpleMeterRegistry(), 2, true);
    PowService.Challenge challenge = service.issue();
    assertThat(service.verifyAndConsume(challenge.challenge(), "0")).isFalse();
  }

  @Test
  void verifyRejectsUnknownChallenge() {
    PowService service = new PowService(redis, new SimpleMeterRegistry(), 2, true);
    assertThat(service.verifyAndConsume("deadbeefcafe", "12345")).isFalse();
  }

  @Test
  void verifyRejectsBlankInput() {
    PowService service = new PowService(redis, new SimpleMeterRegistry(), 2, true);
    assertThat(service.verifyAndConsume(null, "x")).isFalse();
    assertThat(service.verifyAndConsume("c", null)).isFalse();
    assertThat(service.verifyAndConsume("", "")).isFalse();
  }

  private static String mineProof(String challenge, int difficulty) {
    HexFormat hex = HexFormat.of();
    for (long i = 0; i < 1_000_000; i++) {
      String nonce = String.valueOf(i);
      try {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        String h =
            hex.formatHex(
                md.digest(
                    (challenge + ":" + nonce).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        boolean ok = true;
        for (int z = 0; z < difficulty; z++) {
          if (h.charAt(z) != '0') {
            ok = false;
            break;
          }
        }
        if (ok) return nonce;
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
    throw new IllegalStateException("could not mine proof");
  }
}
