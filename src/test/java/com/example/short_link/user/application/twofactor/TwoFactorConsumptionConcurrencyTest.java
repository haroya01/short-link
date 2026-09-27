package com.example.short_link.user.application.twofactor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
class TwoFactorConsumptionConcurrencyTest {

  private static final Instant NOW = Instant.parse("2026-09-12T00:00:15Z");
  @Autowired private TwoFactorService service;
  @Autowired private UserRepository users;
  @Autowired private JdbcTemplate jdbc;

  @MockitoBean(enforceOverride = true)
  private Clock clock;

  private Long userId;
  private String secret;
  private List<String> recovery;

  @BeforeEach
  void enrollCommittedUser() {
    when(clock.instant()).thenReturn(NOW);
    String unique = UUID.randomUUID().toString();
    userId = users.save(new UserEntity(unique + "@twofa.test", "google", unique)).getId();
    secret = service.start(userId).secret();
    recovery = service.confirm(userId, codeAt(NOW));
  }

  @AfterEach
  void deleteFixture() {
    if (userId == null) return;
    jdbc.update("DELETE FROM user_two_factor WHERE user_id = ?", userId);
    jdbc.update("DELETE FROM users WHERE id = ?", userId);
  }

  @Test
  void concurrentRecoveryReplaySucceedsOnlyOnce() throws Exception {
    var results =
        concurrently(
            () -> service.verifyRecovery(userId, recovery.getFirst()),
            () -> service.verifyRecovery(userId, recovery.getFirst()));

    assertThat(results).containsExactlyInAnyOrder(true, false);
    assertThat(storedHashes()).hasSize(9);
    assertThat(service.verifyRecovery(userId, recovery.getFirst())).isFalse();
    assertThat(
            jdbc.queryForObject(
                "SELECT last_verified_step FROM user_two_factor WHERE user_id = ?",
                Long.class,
                userId))
        .isNull();
  }

  @Test
  void concurrentDifferentRecoveryCodesBothStayConsumed() throws Exception {
    assertThat(
            concurrently(
                () -> service.verifyRecovery(userId, recovery.get(0)),
                () -> service.verifyRecovery(userId, recovery.get(1))))
        .containsExactly(true, true);

    assertThat(storedHashes()).hasSize(8);
    assertThat(service.verifyRecovery(userId, recovery.get(0))).isFalse();
    assertThat(service.verifyRecovery(userId, recovery.get(1))).isFalse();
    assertThat(service.verifyRecovery(userId, recovery.get(2))).isTrue();
  }

  @Test
  void firstLoginAfterEnrollmentSucceedsOnceAndNextStepCanAuthenticate() throws Exception {
    String sameCode = codeAt(NOW);
    assertThat(
            concurrently(
                () -> service.verify(userId, sameCode), () -> service.verify(userId, sameCode)))
        .containsExactlyInAnyOrder(true, false);
    assertThat(
            jdbc.queryForObject(
                "SELECT last_verified_step FROM user_two_factor WHERE user_id = ?",
                Long.class,
                userId))
        .isEqualTo(NOW.getEpochSecond() / TotpCodec.PERIOD_SECONDS);

    when(clock.instant()).thenReturn(NOW.plusSeconds(30));
    // The previous code remains within the accepted drift window, but has already been consumed.
    assertThat(service.verify(userId, sameCode)).isFalse();
    assertThat(service.verify(userId, codeAt(NOW.plusSeconds(30)))).isTrue();
    assertThat(service.verify(userId, codeAt(NOW.plusSeconds(30)))).isFalse();
    assertThat(storedHashes()).hasSize(10);
  }

  private String codeAt(Instant instant) {
    return TotpCodec.generateCode(secret, instant.getEpochSecond() / TotpCodec.PERIOD_SECONDS);
  }

  private List<String> storedHashes() {
    return jdbc.queryForObject(
            "SELECT recovery_codes FROM user_two_factor WHERE user_id = ?", String.class, userId)
        .lines()
        .toList();
  }

  private List<Boolean> concurrently(Callable<Boolean> first, Callable<Boolean> second)
      throws Exception {
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var tasks =
          List.of(first, second).stream()
              .map(
                  action ->
                      executor.submit(
                          () -> {
                            ready.countDown();
                            if (!start.await(10, TimeUnit.SECONDS))
                              throw new AssertionError("start signal timed out");
                            return action.call();
                          }))
              .toList();
      assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      return List.of(
          tasks.get(0).get(15, TimeUnit.SECONDS), tasks.get(1).get(15, TimeUnit.SECONDS));
    } finally {
      start.countDown();
    }
  }
}
