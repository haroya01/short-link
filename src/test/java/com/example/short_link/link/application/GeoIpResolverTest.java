package com.example.short_link.link.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class GeoIpResolverTest {

  @Autowired private GeoIpResolver resolver;

  @Test
  void resolvesKoreanIpToKr() {
    assertThat(resolver.resolveCountry("211.115.106.1")).isEqualTo("KR");
  }

  @Test
  void resolvesUsIpToUs() {
    assertThat(resolver.resolveCountry("8.8.8.8")).isEqualTo("US");
  }

  @Test
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
