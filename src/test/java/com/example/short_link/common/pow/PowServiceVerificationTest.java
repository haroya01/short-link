package com.example.short_link.common.pow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/**
 * Boundary tests use the real proof, store and metrics implementations with an observed Redis API.
 */
@ExtendWith(MockitoExtension.class)
class PowServiceVerificationTest {
  private static final String CHALLENGE = "0123456789abcdef0123456789abcdef";
  private static final String VALID_NONCE = "151";

  @Mock private StringRedisTemplate redis;
  @Mock private ValueOperations<String, String> values;
  private SimpleMeterRegistry metrics;
  private PowService service;

  @BeforeEach
  void composeRealVerificationComponents() {
    metrics = new SimpleMeterRegistry();
    service =
        new PowService(
            new PowProof(),
            new PowChallengeStore(redis),
            new PowMetrics(metrics),
            new PowProperties(2, true));
  }

  @Test
  void issuedChallengeKeepsTheWireFormatTtlAndMetric() {
    when(redis.opsForValue()).thenReturn(values);

    PowService.Challenge issued = service.issue();

    assertThat(issued.challenge()).matches("[0-9a-f]{32}");
    assertThat(issued.difficulty()).isEqualTo(2);
    assertThat(service.isEnforced()).isTrue();
    verify(values).set("pow:challenge:" + issued.challenge(), "1", Duration.ofMinutes(5));
    assertThat(metrics.get("pow.challenge.issued").counter().count()).isEqualTo(1);
  }

  @ParameterizedTest
  @MethodSource("missingInputs")
  void missingInputNeverTouchesRedis(String challenge, String nonce) {
    assertThat(service.verifyAndConsume(challenge, nonce)).isFalse();

    verifyNoInteractions(redis);
    assertThat(metrics.get("pow.verify").tag("result", "missing").counter().count()).isEqualTo(1);
  }

  private static Stream<Arguments> missingInputs() {
    return Stream.of(
        Arguments.of(null, VALID_NONCE),
        Arguments.of(CHALLENGE, null),
        Arguments.of("", VALID_NONCE),
        Arguments.of(CHALLENGE, ""),
        Arguments.of(" \t", VALID_NONCE),
        Arguments.of(CHALLENGE, " \n"));
  }

  @Test
  void badProofNeverTouchesRedis() {
    // SHA-256(CHALLENGE + ":0") starts with ca6f, so this proof is always invalid.
    assertThat(service.verifyAndConsume(CHALLENGE, "0")).isFalse();

    verifyNoInteractions(redis);
    assertThat(metrics.get("pow.verify").tag("result", "bad_proof").counter().count()).isEqualTo(1);
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(booleans = false)
  void validProofRequiresSuccessfulAtomicDeletion(Boolean deleted) {
    when(redis.delete(anyString())).thenReturn(deleted);

    assertThat(service.verifyAndConsume(CHALLENGE, VALID_NONCE)).isFalse();

    verify(redis).delete("pow:challenge:" + CHALLENGE);
    verifyNoMoreInteractions(redis);
    assertThat(metrics.get("pow.verify").tag("result", "unknown_or_used").counter().count())
        .isEqualTo(1);
  }
}
