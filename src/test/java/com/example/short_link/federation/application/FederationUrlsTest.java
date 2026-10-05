package com.example.short_link.federation.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class FederationUrlsTest {

  private final FederationUrls urls =
      new FederationUrls(new FederationProperties("https://kurl.me/", "https://blog.kurl.me"));

  @Test
  void onlyOurOwnActorUrlsNameALocalActor() {
    assertThat(urls.publicIdOf("https://kurl.me/ap/actors/abc123")).contains("abc123");
    assertThat(urls.publicIdOf(urls.actor("pid9"))).contains("pid9");

    assertThat(urls.publicIdOf("https://evil.example/ap/actors/abc123")).isEmpty();
    assertThat(urls.publicIdOf("http://kurl.me/ap/actors/abc123")).isEmpty();
    assertThat(urls.publicIdOf("https://kurl.me/ap/actors/abc123/inbox")).isEmpty();
    assertThat(urls.publicIdOf("https://kurl.me/ap/actors/ABC")).isEmpty();
    assertThat(urls.publicIdOf("https://kurl.me/ap/actors/")).isEmpty();
    assertThat(urls.publicIdOf("https://kurl.me/ap/actors/abc123#main-key")).isEmpty();
    assertThat(urls.publicIdOf(null)).isEqualTo(Optional.empty());
  }
}
