package com.example.short_link.link.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class GeoIpResolverTest {

  private static final long REAL_MMDB_MIN_BYTES = 1_000_000L;

  @Autowired private GeoIpResolver resolver;

  static boolean realDatabasePresent() {
    java.io.File f = new java.io.File("src/main/resources/geoip/GeoLite2-City.mmdb");
    return f.exists() && f.length() >= REAL_MMDB_MIN_BYTES;
  }

  @BeforeEach
  void requireRealDatabase() {
    org.junit.jupiter.api.Assumptions.assumeTrue(
        realDatabasePresent(),
        "real GeoLite2-City.mmdb not present (likely MaxMind unavailable in CI); skipping IP-resolution checks");
  }

  @Test
  @EnabledIf("realDatabasePresent")
  void resolvesKoreanIpToKr() {
    assertThat(resolver.resolveCountry("211.115.106.1")).isEqualTo("KR");
  }

  @Test
  @EnabledIf("realDatabasePresent")
  void resolvesUsIpToUs() {
    assertThat(resolver.resolveCountry("8.8.8.8")).isEqualTo("US");
  }

  @Test
  @EnabledIf("realDatabasePresent")
  void resolvesJapaneseIpToJp() {
    assertThat(resolver.resolveCountry("210.155.141.200")).isEqualTo("JP");
  }

  @Test
  void returnsNullForNullIp() {
    assertThat(resolver.resolveCountry(null)).isNull();
  }

  @Test
  void returnsNullForBlankIp() {
    assertThat(resolver.resolveCountry("   ")).isNull();
  }

  @Test
  void returnsNullForPrivateIp() {
    assertThat(resolver.resolveCountry("127.0.0.1")).isNull();
  }

  @Test
  void returnsNullForInvalidIp() {
    assertThat(resolver.resolveCountry("not-an-ip")).isNull();
  }
}
