package com.example.short_link.testsupport;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

/**
 * Runs HTTP tests against an actual server and disposable MySQL/Redis instances. Test data must be
 * committed before HTTP requests; a test-managed transaction cannot span the server's request
 * thread.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
@Execution(ExecutionMode.SAME_THREAD)
@Isolated("Each HTTP test class owns its database, Redis state, and query captures")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class DockerHttpTest {

  @Autowired
  @Qualifier("taskExecutor")
  private ThreadPoolTaskExecutor taskExecutor;

  @Autowired
  @Qualifier("webhookExecutor")
  private ThreadPoolTaskExecutor webhookExecutor;

  /** Wait for the real queues, including work enqueued by another background task. */
  protected void awaitAsyncWork() {
    long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
    long idleSince = -1;
    List<ThreadPoolTaskExecutor> executors = List.of(taskExecutor, webhookExecutor);
    while (true) {
      boolean busy =
          executors.stream()
              .anyMatch(
                  executor ->
                      executor.getActiveCount() != 0
                          || !executor.getThreadPoolExecutor().getQueue().isEmpty());
      if (busy) idleSince = -1;
      else if (idleSince < 0) idleSince = System.nanoTime();
      else if (System.nanoTime() - idleSince >= Duration.ofMillis(50).toNanos()) return;
      if (System.nanoTime() >= deadline) {
        throw new AssertionError("HTTP-triggered background work did not finish within 30 seconds");
      }
      try {
        Thread.sleep(10);
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
        throw new AssertionError(
            "Interrupted while awaiting HTTP-triggered background work", interrupted);
      }
    }
  }

  private static final String REDIS_PASSWORD = "http-test-redis";

  @Container
  private static final MySQLContainer MYSQL =
      new MySQLContainer("mysql:8.0.33")
          .withDatabaseName("short_link_http_test")
          .withUsername("short_link")
          .withPassword("http-test-mysql")
          .withEnv("TZ", "Asia/Seoul")
          .withCommand(
              "--character-set-server=utf8mb4",
              "--collation-server=utf8mb4_unicode_ci",
              "--default-time-zone=+09:00")
          .withUrlParam("useUnicode", "true")
          .withUrlParam("characterEncoding", "utf8")
          .withUrlParam("serverTimezone", "Asia/Seoul");

  @Container
  private static final GenericContainer<?> REDIS =
      new GenericContainer<>("redis:7")
          .withExposedPorts(6379)
          .withCommand("redis-server", "--requirepass", REDIS_PASSWORD);

  @DynamicPropertySource
  static void useContainerConnections(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
    registry.add("spring.datasource.username", MYSQL::getUsername);
    registry.add("spring.datasource.password", MYSQL::getPassword);
    registry.add("spring.data.redis.host", REDIS::getHost);
    registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    registry.add("spring.data.redis.password", () -> REDIS_PASSWORD);
  }
}
