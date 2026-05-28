package com.example.short_link.link.classifier.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import org.junit.jupiter.api.Assumptions;
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
    File f = new File("src/main/resources/geoip/GeoLite2-City.mmdb");
    return f.exists() && f.length() >= REAL_MMDB_MIN_BYTES;
  }

  @BeforeEach
  void requireRealDatabase() {
    Assumptions.assumeTrue(
        realDatabasePresent(),
        "real GeoLite2-City.mmdb not present (likely MaxMind unavailable in CI); skipping IP-resolution checks");
  }

  @Test
  @EnabledIf("realDatabasePresent")
  void resolvesKoreanIpToKr() {
    assertThat(resolver.resolve("211.115.106.1").countryCode()).isEqualTo("KR");
  }

  @Test
  @EnabledIf("realDatabasePresent")
  void resolvesUsIpToUs() {
    assertThat(resolver.resolve("8.8.8.8").countryCode()).isEqualTo("US");
  }

  @Test
  @EnabledIf("realDatabasePresent")
  void resolvesJapaneseIpToJp() {
    assertThat(resolver.resolve("210.155.141.200").countryCode()).isEqualTo("JP");
  }

  @Test
  void returnsNullForNullIp() {
    assertThat(resolver.resolve(null).countryCode()).isNull();
  }

  @Test
  void returnsNullForBlankIp() {
    assertThat(resolver.resolve("   ").countryCode()).isNull();
  }

  @Test
  void returnsNullForPrivateIp() {
    assertThat(resolver.resolve("127.0.0.1").countryCode()).isNull();
  }

  @Test
  void returnsNullForInvalidIp() {
    assertThat(resolver.resolve("not-an-ip").countryCode()).isNull();
  }
}
