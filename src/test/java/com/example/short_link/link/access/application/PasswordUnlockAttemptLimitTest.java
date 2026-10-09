package com.example.short_link.link.access.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.link.access.application.PasswordUnlockResult.Rejected;
import com.example.short_link.link.access.application.PasswordUnlockResult.RejectionReason;
import com.example.short_link.link.access.application.write.PasswordUnlockUseCase;
import com.example.short_link.link.application.write.CreateLinkCommand;
import com.example.short_link.link.application.write.CreateLinkUseCase;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.redirect.application.RedirectVisit;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class PasswordUnlockAttemptLimitTest {
  private static final int LIMIT = 10;

  @Autowired private PasswordUnlockUseCase unlock;
  @Autowired private CreateLinkUseCase createLink;
  @Autowired private UserRepository users;
  @Autowired private StringRedisTemplate redis;
  @Autowired private JdbcTemplate jdbc;

  private final List<Long> createdUsers = new ArrayList<>();
  private final List<String> createdCodes = new ArrayList<>();

  @AfterEach
  void deleteCommittedRowsAndCounters() {
    for (String code : createdCodes) {
      redis.delete(redis.keys("pwd-attempt:" + code + ":*"));
    }
    for (Long userId : createdUsers) {
      jdbc.update("DELETE FROM link WHERE user_id = ?", userId);
      jdbc.update("DELETE FROM users WHERE id = ?", userId);
    }
  }

  @Test
  void concurrentWrongGuessesReachThePasswordCheckOnlyUpToTheLimit() throws Exception {
    ShortCode code = protectedLink();
    int guesses = LIMIT + 6;
    CountDownLatch start = new CountDownLatch(1);
    List<Callable<PasswordUnlockResult>> attempts = new ArrayList<>();
    for (int i = 0; i < guesses; i++) {
      attempts.add(
          () -> {
            start.await();
            return unlock.execute(code, "wrong-guess", null, visit("203.0.113.20"));
          });
    }
    ExecutorService pool = Executors.newFixedThreadPool(guesses);
    List<PasswordUnlockResult> results = new ArrayList<>();
    try {
      List<Future<PasswordUnlockResult>> pending = new ArrayList<>();
      for (Callable<PasswordUnlockResult> attempt : attempts) {
        pending.add(pool.submit(attempt));
      }
      start.countDown();
      for (Future<PasswordUnlockResult> done : pending) {
        results.add(done.get());
      }
    } finally {
      pool.shutdownNow();
    }

    assertThat(results.stream().filter(new Rejected(RejectionReason.WRONG_PASSWORD)::equals))
        .hasSize(LIMIT);
    assertThat(results.stream().filter(new Rejected(RejectionReason.LOCKED_OUT)::equals))
        .hasSize(guesses - LIMIT);
  }

  @Test
  void addressesInOneIpv6Slash64ShareTheLimit() {
    ShortCode code = protectedLink();
    for (int i = 1; i <= LIMIT; i++) {
      assertThat(
              unlock.execute(
                  code, "wrong-guess", null, visit("2001:db8:1:2::" + Integer.toHexString(i))))
          .isEqualTo(new Rejected(RejectionReason.WRONG_PASSWORD));
    }

    assertThat(unlock.execute(code, "wrong-guess", null, visit("2001:db8:1:2:ffff:ffff:ffff:ffff")))
        .isEqualTo(new Rejected(RejectionReason.LOCKED_OUT));
    assertThat(unlock.execute(code, "wrong-guess", null, visit("2001:db8:1:3::1")))
        .isEqualTo(new Rejected(RejectionReason.WRONG_PASSWORD));
  }

  private ShortCode protectedLink() {
    String tag = UUID.randomUUID().toString().substring(0, 8);
    UserEntity owner = users.save(new UserEntity("pwd-" + tag + "@example.com", "google", tag));
    createdUsers.add(owner.getId());
    ShortCode code =
        createLink
            .execute(
                CreateLinkCommand.of(
                    "https://example.com/locked-" + tag, owner.getId(), null, null, "open-sesame"))
            .shortCode();
    createdCodes.add(code.value());
    return code;
  }

  private static RedirectVisit visit(String clientIp) {
    return new RedirectVisit(null, "Mozilla/5.0", clientIp, "en", null, null, false, null, false);
  }
}
