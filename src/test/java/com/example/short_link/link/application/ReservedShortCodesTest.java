package com.example.short_link.link.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.link.application.helper.ReservedShortCodes;
import org.junit.jupiter.api.Test;

class ReservedShortCodesTest {

  @Test
  void blocksFrontendRoutes() {
    assertThat(ReservedShortCodes.isReserved("login")).isTrue();
    assertThat(ReservedShortCodes.isReserved("dashboard")).isTrue();
    assertThat(ReservedShortCodes.isReserved("admin")).isTrue();
    assertThat(ReservedShortCodes.isReserved("stats")).isTrue();
    assertThat(ReservedShortCodes.isReserved("auth")).isTrue();
    assertThat(ReservedShortCodes.isReserved("demo")).isTrue();
    assertThat(ReservedShortCodes.isReserved("showcase")).isTrue();
  }

  @Test
  void blocksApiPaths() {
    assertThat(ReservedShortCodes.isReserved("api")).isTrue();
    assertThat(ReservedShortCodes.isReserved("v1")).isTrue();
    assertThat(ReservedShortCodes.isReserved("oauth2")).isTrue();
    assertThat(ReservedShortCodes.isReserved("actuator")).isTrue();
  }

  @Test
  void blocksOperationalPaths() {
    assertThat(ReservedShortCodes.isReserved("favicon")).isTrue();
    assertThat(ReservedShortCodes.isReserved("robots")).isTrue();
    assertThat(ReservedShortCodes.isReserved("sitemap")).isTrue();
  }

  @Test
  void caseInsensitive() {
    assertThat(ReservedShortCodes.isReserved("LOGIN")).isTrue();
    assertThat(ReservedShortCodes.isReserved("Admin")).isTrue();
    assertThat(ReservedShortCodes.isReserved("API")).isTrue();
  }

  @Test
  void allowsNormalCodes() {
    assertThat(ReservedShortCodes.isReserved("abc1234")).isFalse();
    assertThat(ReservedShortCodes.isReserved("myLink")).isFalse();
    assertThat(ReservedShortCodes.isReserved("xyz")).isFalse();
  }

  @Test
  void handlesNull() {
    assertThat(ReservedShortCodes.isReserved(null)).isFalse();
  }
}
