package com.example.short_link;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import com.example.short_link.common.counter.RedisWindowCounter;
import com.example.short_link.common.lock.RedisDistributedLock;
import com.example.short_link.link.application.dto.CachedLink;
import com.example.short_link.link.application.read.CachedLinkLoader;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.moderation.application.LinkModerationService;
import com.example.short_link.link.moderation.domain.LinkDisableReason;
import com.example.short_link.support.FaultyRedis;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
@ActiveProfiles("test")
class RedisOutageIntegrationTest {

  private static final FaultyRedis REDIS = FaultyRedis.inFrontOfTheTestRedis();
  private static final Duration ONE_COMMAND = Duration.ofSeconds(3);

  @DynamicPropertySource
  static void throughTheFaultyLine(DynamicPropertyRegistry registry) {
    REDIS.route(registry);
  }

  @Autowired private RedisWindowCounter counter;
  @Autowired private RedisDistributedLock lock;
  @Autowired private CachedLinkLoader links;
  @Autowired private LinkRepository linkRepository;
  @Autowired private StringRedisTemplate redis;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private LinkModerationService moderation;

  private String code;

  @AfterEach
  void healAndForget() {
    REDIS.heal();
    assertTimeoutPreemptively(Duration.ofSeconds(40), this::waitUntilRedisAnswers);
    if (code != null) {
      jdbc.update(
          "DELETE m FROM link_moderation m JOIN link l ON l.id = m.link_id WHERE l.short_code = ?",
          code);
      jdbc.update("DELETE FROM link WHERE short_code = ?", code);
    }
  }

  @Test
  void aRedisThatStopsAnsweringFailsACommandWithinTheTimeout() {
    REDIS.hang();

    assertTimeoutPreemptively(
        ONE_COMMAND,
        () ->
            assertThatThrownBy(() -> counter.increment(key(), Duration.ofMinutes(1)))
                .isInstanceOf(DataAccessException.class));
  }

  @Test
  void aRedisThatWentAwayFailsACommandWithinTheTimeoutInsteadOfQueueingIt() {
    counter.increment(key(), Duration.ofMinutes(1));
    REDIS.drop();

    assertTimeoutPreemptively(
        ONE_COMMAND,
        () ->
            assertThatThrownBy(() -> lock.tryAcquire(key(), Duration.ofMinutes(1)))
                .isInstanceOf(DataAccessException.class));
  }

  @Test
  void aRedirectLookupFallsBackToTheDatabaseWithinTheTimeoutWhileRedisHangs() {
    code = "ro" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    linkRepository.save(
        new LinkEntity("https://example.com/landing", code, null, Instant.now().plusSeconds(3600)));
    REDIS.hang();

    CachedLink link =
        assertTimeoutPreemptively(
            ONE_COMMAND.multipliedBy(2), () -> links.loadByShortCode(new ShortCode(code)));

    assertThat(link.originalUrl()).isEqualTo("https://example.com/landing");
  }

  @Test
  void aLinkTakenDownWhileRedisIsAwayStaysTakenDownWithoutFailingTheCall() {
    code = "ro" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    linkRepository.save(
        new LinkEntity("https://example.com/landing", code, null, Instant.now().plusSeconds(3600)));
    REDIS.drop();

    boolean disabled =
        assertTimeoutPreemptively(
            ONE_COMMAND.multipliedBy(2),
            () -> moderation.disable(null, new ShortCode(code), LinkDisableReason.ADMIN));

    assertThat(disabled).isTrue();
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM link_moderation m JOIN link l ON l.id = m.link_id"
                    + " WHERE l.short_code = ?",
                Long.class,
                code))
        .isEqualTo(1L);
  }

  private void waitUntilRedisAnswers() throws InterruptedException {
    while (true) {
      try {
        redis.opsForValue().get(key());
        return;
      } catch (DataAccessException stillDown) {
        Thread.sleep(200);
      }
    }
  }

  private static String key() {
    return "redis-outage-test:" + UUID.randomUUID();
  }
}
