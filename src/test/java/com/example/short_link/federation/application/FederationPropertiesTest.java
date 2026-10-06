package com.example.short_link.federation.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FederationPropertiesTest {

  @Test
  void domainIsTheHostAndKeepsANonDefaultPort() {
    assertThat(new FederationProperties("https://kurl.me/", "https://blog.kurl.me/").domain())
        .isEqualTo("kurl.me");
    assertThat(new FederationProperties("http://localhost:8080", null).domain())
        .isEqualTo("localhost:8080");
  }

  @Test
  void blankValuesFallBackAndTrailingSlashesAreTrimmed() {
    FederationProperties props = new FederationProperties(" ", "");
    assertThat(props.baseUrl()).isEqualTo("http://localhost:8080");
    assertThat(props.profileBaseUrl()).isEqualTo("https://blog.kurl.me");
    assertThat(new FederationProperties("https://kurl.me/", "https://blog.kurl.me/").baseUrl())
        .isEqualTo("https://kurl.me");
  }

  @Test
  void urlsHangOffTheActorAndEncodeTheProfileHandle() {
    FederationUrls urls =
        new FederationUrls(new FederationProperties("https://kurl.me", "https://blog.kurl.me"));
    assertThat(urls.actor("abc")).isEqualTo("https://kurl.me/ap/actors/abc");
    assertThat(urls.key("abc")).isEqualTo("https://kurl.me/ap/actors/abc#main-key");
    assertThat(urls.inbox("abc")).isEqualTo("https://kurl.me/ap/actors/abc/inbox");
    assertThat(urls.outbox("abc")).isEqualTo("https://kurl.me/ap/actors/abc/outbox");
    assertThat(urls.followers("abc")).isEqualTo("https://kurl.me/ap/actors/abc/followers");
    assertThat(urls.following("abc")).isEqualTo("https://kurl.me/ap/actors/abc/following");
    assertThat(urls.sharedInbox()).isEqualTo("https://kurl.me/ap/inbox");
    assertThat(urls.nodeInfo()).isEqualTo("https://kurl.me/ap/nodeinfo/2.1");
    assertThat(urls.profile("yuki")).isEqualTo("https://blog.kurl.me/@yuki");
    assertThat(urls.domain()).isEqualTo("kurl.me");
  }
}
