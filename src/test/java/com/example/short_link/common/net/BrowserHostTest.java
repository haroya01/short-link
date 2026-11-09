package com.example.short_link.common.net;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class BrowserHostTest {

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "https://phish.test/login|phish.test",
        "HTTPS://PHISH.TEST/login|phish.test",
        "https:///phish.test/login|phish.test",
        "https:////phish.test/login|phish.test",
        "https:/phish.test/login|phish.test",
        "https:phish.test/login|phish.test",
        "https:\\\\phish.test\\login|phish.test",
        "https://phish.test./login|phish.test",
        "https://phish.test..|phish.test",
        "https://a_b.phish.test/login|a_b.phish.test",
        "https://phish%2Etest/login|phish.test",
        "https://%70hish.test/login|phish.test",
        "https://ｐhish.test/login|phish.test",
        "https://phish。test/login|phish.test",
        "https://user:pass@phish.test:8443/login|phish.test",
        "https://good.test@phish.test/login|phish.test",
        "https://phish.test?q=1|phish.test",
        "https://phish.test#frag|phish.test",
        "https://ph\tish.test/|phish.test",
        "'  https://phish.test/  '|phish.test",
        "https://bücher.example/|xn--bcher-kva.example",
        "https://[2001:db8::1]:8443/x|[2001:db8::1]",
        "http://127.0.0.1:8080/|127.0.0.1"
      })
  void findsTheHostABrowserWouldOpen(String url, String host) {
    assertThat(BrowserHost.of(url)).isEqualTo(host);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(
      strings = {
        "javascript:alert(1)",
        "mailto:a@phish.test",
        "phish.test/login",
        "https://",
        "https:///"
      })
  void hasNoHostForAnythingButAWebAddress(String url) {
    assertThat(BrowserHost.of(url)).isNull();
  }
}
