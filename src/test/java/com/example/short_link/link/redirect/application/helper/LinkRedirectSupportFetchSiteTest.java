package com.example.short_link.link.redirect.application.helper;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;

class LinkRedirectSupportFetchSiteTest {

  private static MockHttpServletRequest withHeader(String value) {
    MockHttpServletRequest req = new MockHttpServletRequest();
    if (value != null) req.addHeader("Sec-Fetch-Site", value);
    return req;
  }

  @ParameterizedTest
  @CsvSource({
    "none,none",
    "cross-site,cross-site",
    "same-site,same-site",
    "same-origin,same-origin",
    "NONE,none",
    "  cross-site  ,cross-site",
  })
  void keepsTheFourSpecValues(String header, String expected) {
    assertThat(LinkRedirectSupport.fetchSite(withHeader(header))).isEqualTo(expected);
  }

  /** 스펙 밖 값은 위조·쓰레기라 저장하지 않는다 — 모르는 걸 그럴듯한 값으로 남기면 집계가 거짓말을 한다. */
  @ParameterizedTest
  @ValueSource(strings = {"", "  ", "cross_site", "same site", "1", "<script>"})
  void dropsAnythingElse(String header) {
    assertThat(LinkRedirectSupport.fetchSite(withHeader(header))).isNull();
  }

  /** 헤더를 안 보내는 구형 브라우저·비브라우저는 null 그대로(백필 불가). */
  @Test
  void missingHeaderIsNull() {
    assertThat(LinkRedirectSupport.fetchSite(withHeader(null))).isNull();
  }
}
