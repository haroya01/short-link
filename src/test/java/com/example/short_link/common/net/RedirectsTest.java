package com.example.short_link.common.net;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;

class RedirectsTest {

  private static final URI FROM = URI.create("https://a.example/one/two?q=1");

  @Test
  void onlyTheFiveMovesAreRedirects() {
    assertThat(List.of(301, 302, 303, 307, 308)).allMatch(Redirects::isRedirect);
    assertThat(List.of(200, 300, 304, 305, 306, 404)).noneMatch(Redirects::isRedirect);
  }

  @Test
  void aLocationResolvesAgainstThePageThatSentIt() {
    assertThat(Redirects.next(FROM, " ../three ")).contains(URI.create("https://a.example/three"));
    assertThat(Redirects.next(FROM, "https://b.example/x"))
        .contains(URI.create("https://b.example/x"));
  }

  @Test
  void aMissingOrMalformedLocationGoesNowhere() {
    assertThat(Redirects.next(FROM, null)).isEmpty();
    assertThat(Redirects.next(FROM, "  ")).isEmpty();
    assertThat(Redirects.next(FROM, "https://b.example/a b")).isEmpty();
  }
}
