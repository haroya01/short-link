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

// 빈 풀에서 30초 기다리지 않고 바로 실패하는지, 커넥션을 오래 쥔 호출의 스택이 로그에 남는지 확인한다.
// 풀을 비운 원인 쿼리를 재현하는 테스트는 아니다.
class DataSourcePoolHardeningTest {

  @Test
  void exhaustedPoolFailsFastInsteadOfHanging() throws Exception {
    HikariConfig cfg = baseConfig("hardening-failfast");
    cfg.setMaximumPoolSize(1);
    cfg.setConnectionTimeout(500); // 운영은 10_000ms, 테스트라 짧게 둔다.
    try (HikariDataSource ds = new HikariDataSource(cfg)) {
      Connection held = ds.getConnection();
      long start = System.nanoTime();
      SQLTransientConnectionException ex =
          assertThrows(SQLTransientConnectionException.class, ds::getConnection);
      long elapsedMs = (System.nanoTime() - start) / 1_000_000;

      assertThat(ex.getMessage()).contains("Connection is not available");
      assertThat(elapsedMs)
          .as("failed fast near connectionTimeout, did not hang")
          .isBetween(400L, 3000L);
      held.close();
    }
  }

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
    cfg.setLeakDetectionThreshold(2000); // Hikari 하한이 2000ms다(운영은 20_000ms).
    try (HikariDataSource ds = new HikariDataSource(cfg)) {
      Connection leaked = ds.getConnection();
      Thread.sleep(2500);

      ILoggingEvent warn =
          appender.list.stream()
              .filter(e -> e.getLevel() == Level.WARN)
              .filter(e -> e.getFormattedMessage().toLowerCase().contains("leak"))
              .findFirst()
              .orElse(null);

      assertThat(warn).as("Hikari logged a connection-leak WARN").isNotNull();
      assertThat(warn.getThrowableProxy()).as("holder stack trace is captured").isNotNull();
      leaked.close();
    } finally {
      hikari.detachAppender(appender);
      hikari.setLevel(prior);
    }
  }

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

  // Spring 테스트와 같은 환경변수 재정의를 사용해 독립 Hikari 풀도 격리된 테스트 DB를 향한다.
  private static HikariConfig baseConfig(String poolName) {
    Map<String, Object> test = loadYaml("/application-test.yml");
    HikariConfig c = new HikariConfig();
    c.setPoolName(poolName);
    c.setJdbcUrl(testDataSourceProperty(test, "url"));
    c.setUsername(testDataSourceProperty(test, "username"));
    c.setPassword(testDataSourceProperty(test, "password"));
    c.setDriverClassName("com.mysql.cj.jdbc.Driver");
    return c;
  }

  private static String testDataSourceProperty(Map<String, Object> test, String property) {
    return System.getenv()
        .getOrDefault(
            "SPRING_DATASOURCE_" + property.toUpperCase(java.util.Locale.ROOT),
            (String) dig(test, "spring", "datasource", property));
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
