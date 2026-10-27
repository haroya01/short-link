package com.example.short_link.federation.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ActivityStreamsTest {

  @ParameterizedTest
  @CsvSource({
    "https://mastodon.example/users/a, https://mastodon.example/@a/1, true",
    "https://Mastodon.Example/users/a, https://mastodon.example/@a/1, true",
    "http://mastodon.example/users/a, https://mastodon.example/@a/1, true",
    "https://mastodon.example/users/a, https://files.mastodon.example/a.png, false",
    "https://mastodon.example:8443/users/a, https://mastodon.example/@a/1, false",
    "https://mastodon.example/users/a, ftp://mastodon.example/a, false",
    "urn:uuid:1, urn:uuid:1, false",
    "not a uri, not a uri, false",
    "https://mastodon.example/users/a, not a uri, false"
  })
  void twoAddressesShareAHostOnlyWhenBothNameTheSameHttpServer(
      String uri, String other, boolean same) {
    assertThat(ActivityStreams.sameHost(uri, other)).isEqualTo(same);
  }
}
