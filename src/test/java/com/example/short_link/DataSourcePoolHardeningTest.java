package com.example.short_link;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.sql.Connection;
import java.sql.SQLTransientConnectionException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

/**
 * 2026-06-22 prod 커넥션 풀 4분 고갈(PR #570) 대응 — 우리가 넣은 두 안전장치가 실제로 동작함을 증명한다. 풀을 일부러 굶겨 Hikari 메커니즘을 직접
 * 검증한다(Spring 컨텍스트 없이 독립 DataSource, 테스트 DB 사용).
 *
 * <p>증명 범위(정직): 이 테스트는 "그때 풀을 비운 범인 쿼리"를 재현하지 않는다(그건 부하재현 영역). 대신 재발했을 때 우리가 의지하는 두 가지 — (1) 빈 풀에서
 * 30초 매달리지 않고 빠르게 실패, (2) 커넥션을 오래 쥔 호출의 보유 스택이 로그로 드러남 — 이 실제로 작동함을 못박는다. 다음에 같은 일이 나면 로그가 범인을
 * 가리킨다는 약속을 테스트로 보증하는 것.
 */
class DataSourcePoolHardeningTest {

  /** 빈 풀: 다음 요청은 connectionTimeout 에서 끊겨야 한다(무한 대기 금지). prod 30s→10s 와 같은 메커니즘. */
  @Test
  void exhaustedPoolFailsFastInsteadOfHanging() throws Exception {
    HikariConfig cfg = baseConfig("hardening-failfast");
    cfg.setMaximumPoolSize(1);
    cfg.setConnectionTimeout(500); // prod 는 10_000ms — 여기선 빠른 테스트용, 동작 메커니즘은 동일
    try (HikariDataSource ds = new HikariDataSource(cfg)) {
      Connection held = ds.getConnection(); // 단 하나뿐인 커넥션 — 이제 풀은 비었다
      long start = System.nanoTime();
      // 굶은 풀은 SQLTransientConnectionException 으로 거절한다(우리가 prod 에서 본 바로 그 타입).
      SQLTransientConnectionException ex =
          assertThrows(SQLTransientConnectionException.class, ds::getConnection);
      long elapsedMs = (System.nanoTime() - start) / 1_000_000;

      assertThat(ex.getMessage()).contains("Connection is not available");
      // 핵심: 굶은 풀은 connectionTimeout 즈음에 거절한다 — 매달리지(30s) 않는다.
      assertThat(elapsedMs)
          .as("failed fast near connectionTimeout, did not hang")
          .isBetween(400L, 3000L);
      held.close();
    }
  }

  /** 커넥션을 임계값보다 오래 쥐면 Hikari 가 보유 스택을 WARN 으로 남긴다 — 다음 범인 특정의 근거. */
  @Test
  void leakDetectionLogsTheHoldersStack() throws Exception {
    ch.qos.logback.classic.Logger hikari =
        (ch.qos.logback.classic.Logger) LoggerFactory.getLogger("com.zaxxer.hikari");
    Level prior = hikari.getLevel();
    hikari.setLevel(Level.WARN);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    hikari.addAppender(appender);

    HikariConfig cfg = baseConfig("hardening-leak");
    cfg.setMaximumPoolSize(2);
    cfg.setLeakDetectionThreshold(2000); // Hikari 하한 2000ms (prod 는 20_000ms)
    try (HikariDataSource ds = new HikariDataSource(cfg)) {
      Connection leaked = ds.getConnection();
      Thread.sleep(2500); // 임계값 넘겨 쥐고 있는다 — prod 에서 풀을 비운 바로 그 상황

      ILoggingEvent warn =
          appender.list.stream()
              .filter(e -> e.getLevel() == Level.WARN)
              .filter(e -> e.getFormattedMessage().toLowerCase().contains("leak"))
              .findFirst()
              .orElse(null);

      assertThat(warn).as("Hikari logged a connection-leak WARN").isNotNull();
      // 누가 커넥션을 쥐고 있었는지 스택이 함께 찍힌다 — 재발 시 이걸로 범인 경로를 가린다.
      assertThat(warn.getThrowableProxy()).as("holder stack trace is captured").isNotNull();
      leaked.close();
    } finally {
      hikari.detachAppender(appender);
      hikari.setLevel(prior);
    }
  }

  /** prod 설정이 실제로 강화된 기본값을 선언하는지 — 위 동작 증명을 prod 값과 묶는다. */
  @Test
  void prodConfigDeclaresHardenedPoolDefaults() {
    Map<String, Object> prod = loadYaml("/application-prod.yml");
    String poolMax =
        String.valueOf(dig(prod, "spring", "datasource", "hikari", "maximum-pool-size"));
    String connTimeout =
        String.valueOf(dig(prod, "spring", "datasource", "hikari", "connection-timeout"));
    String leak =
        String.valueOf(dig(prod, "spring", "datasource", "hikari", "leak-detection-threshold"));

    assertThat(poolMax).as("maximum-pool-size default").contains("20");
    assertThat(connTimeout).as("connection-timeout default (ms)").contains("10000");
    assertThat(leak).as("leak-detection-threshold default (ms)").contains("20000");
  }

  // ── helpers ──────────────────────────────────────────────────────────────────────────────────

  /** 테스트 DB(application-test.yml) 로 향하는 독립 Hikari 설정. */
  private static HikariConfig baseConfig(String poolName) {
    Map<String, Object> test = loadYaml("/application-test.yml");
    HikariConfig c = new HikariConfig();
    c.setPoolName(poolName);
    c.setJdbcUrl((String) dig(test, "spring", "datasource", "url"));
    c.setUsername((String) dig(test, "spring", "datasource", "username"));
    c.setPassword((String) dig(test, "spring", "datasource", "password"));
    c.setDriverClassName("com.mysql.cj.jdbc.Driver");
    return c;
  }

  private static Map<String, Object> loadYaml(String classpathResource) {
    try (InputStream in =
        DataSourcePoolHardeningTest.class.getResourceAsStream(classpathResource)) {
      assertThat(in).as("classpath resource " + classpathResource).isNotNull();
      return new Yaml().load(in);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @SuppressWarnings("unchecked")
  private static Object dig(Map<String, Object> root, String... keys) {
    Object cur = root;
    for (String k : keys) {
      assertThat(cur).as("path before '" + k + "'").isInstanceOf(Map.class);
      cur = ((Map<String, Object>) cur).get(k);
    }
    return cur;
  }
}
