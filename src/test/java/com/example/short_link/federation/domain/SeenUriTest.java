package com.example.short_link.federation.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HexFormat;
import org.junit.jupiter.api.Test;

class SeenUriTest {

  @Test
  void aPostIsTheFirstSixteenBytesOfTheSha256OfItsUri() {
    SeenUri uri = SeenUri.of("https://mastodon.social/users/a/statuses/1");

    assertThat(HexFormat.of().formatHex(uri.bytes())).isEqualTo("04993fcd865444ec3268dfa316f9df06");
    assertThat(SeenUri.fromBytes(uri.bytes())).isEqualTo(uri);
  }
}
