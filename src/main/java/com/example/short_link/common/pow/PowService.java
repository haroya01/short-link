package com.example.short_link.common.pow;

import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Hash-cash style proof-of-work for anonymous endpoints. The server hands out a random challenge;
 * the client must find a {@code nonce} such that {@code SHA-256(challenge:nonce)} starts with
 * {@code difficulty} hex zeros. Each challenge is single-use (deleted on verify) with a 5-minute
 * TTL — bots can't precompute a stockpile and replay them later.
 *
 * <p>Why not a captcha widget: a captcha is a third-party dependency (cookies, GDPR, blocked
 * regions). PoW is self-hosted and adds ~50ms of CPU on modern devices at difficulty=4 hex zeros
 * (~65k hashes), which is invisible to humans but multiplies bot operating cost linearly.
 */
@Slf4j
@Service
public class PowService {

  static final Duration CHALLENGE_TTL = Duration.ofMinutes(5);
  static final String KEY_PREFIX = "pow:challenge:";
  private static final SecureRandom RANDOM = new SecureRandom();
  private static final HexFormat HEX = HexFormat.of();

  private final StringRedisTemplate redis;
  private final MeterRegistry meterRegistry;
  private final int difficulty;
  private final boolean enforce;

  public PowService(StringRedisTemplate redis, MeterRegistry meterRegistry, PowProperties props) {
    this.redis = redis;
    this.meterRegistry = meterRegistry;
    this.difficulty = props.difficulty();
    this.enforce = props.enforce();
  }

  public boolean isEnforced() {
    return enforce;
  }

  public Challenge issue() {
    byte[] buf = new byte[16];
    RANDOM.nextBytes(buf);
    String challenge = HEX.formatHex(buf);
    redis.opsForValue().set(KEY_PREFIX + challenge, "1", CHALLENGE_TTL);
    meterRegistry.counter("pow.challenge.issued").increment();
    return new Challenge(challenge, difficulty);
  }

  /**
   * Verifies and consumes the challenge. Returns true only if the challenge was issued by this
   * cluster, hasn't been used yet, and the supplied nonce produces a hash with at least {@code
   * difficulty} hex zeros. The challenge is deleted on success — same proof can't be replayed.
   */
  public boolean verifyAndConsume(String challenge, String nonce) {
    if (challenge == null || nonce == null || challenge.isBlank() || nonce.isBlank()) {
      meterRegistry.counter("pow.verify", "result", "missing").increment();
      return false;
    }
    String hash = sha256Hex(challenge + ":" + nonce);
    if (!hasLeadingZeros(hash, difficulty)) {
      // Reject before touching Redis: a bad nonce must not consume the challenge, otherwise any
      // client could invalidate another's outstanding challenge by replaying its id with garbage.
      meterRegistry.counter("pow.verify", "result", "bad_proof").increment();
      return false;
    }
    // Consume only after the proof checks out. delete() is atomic, so a replay of the same valid
    // proof finds the key already gone and fails.
    String key = KEY_PREFIX + challenge;
    Boolean deleted = redis.delete(key);
    if (deleted == null || !deleted) {
      meterRegistry.counter("pow.verify", "result", "unknown_or_used").increment();
      return false;
    }
    meterRegistry.counter("pow.verify", "result", "ok").increment();
    return true;
  }

  private static boolean hasLeadingZeros(String hex, int zeros) {
    for (int i = 0; i < zeros; i++) {
      if (hex.charAt(i) != '0') return false;
    }
    return true;
  }

  private static String sha256Hex(String input) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      return HEX.formatHex(md.digest(input.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }

  public record Challenge(String challenge, int difficulty) {}
}
