package com.example.short_link.federation.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WebFingerResourceTest {

  private static final String DOMAIN = "kurl.me";
  private static final String ACTOR_PREFIX = "https://kurl.me/ap/actors/";
  private static final String INSTANCE = "https://kurl.me/ap/instance";

  private static Object parse(String resource) {
    return WebFingerResource.parse(resource, DOMAIN, ACTOR_PREFIX, INSTANCE).orElse(null);
  }

  @Test
  void acceptsTheFormsRemoteServersSend() {
    assertThat(parse("acct:yuki@kurl.me")).isEqualTo(new WebFingerResource.Username("yuki"));
    assertThat(parse("ACCT:yuki@KURL.ME")).isEqualTo(new WebFingerResource.Username("yuki"));
    assertThat(parse("yuki@kurl.me")).isEqualTo(new WebFingerResource.Username("yuki"));
    assertThat(parse("@yuki@kurl.me")).isEqualTo(new WebFingerResource.Username("yuki"));
    assertThat(parse("  acct:yuki@kurl.me  ")).isEqualTo(new WebFingerResource.Username("yuki"));
    assertThat(parse("https://kurl.me/ap/actors/abc123"))
        .isEqualTo(new WebFingerResource.ActorId("abc123"));
  }

  @Test
  void theDomainAsAUsernameOrTheInstanceUrlIsTheInstanceActor() {
    assertThat(parse("acct:kurl.me@kurl.me")).isEqualTo(new WebFingerResource.Instance());
    assertThat(parse("KURL.ME@kurl.me")).isEqualTo(new WebFingerResource.Instance());
    assertThat(parse("https://kurl.me/ap/instance")).isEqualTo(new WebFingerResource.Instance());
    assertThat(parse("acct:kurl.me@mastodon.social")).isNull();
  }

  @Test
  void refusesOtherDomainsAndMalformedResources() {
    assertThat(parse("acct:yuki@mastodon.social")).isNull();
    assertThat(parse("acct:yuki")).isNull();
    assertThat(parse("acct:@kurl.me")).isNull();
    assertThat(parse("acct:yuki@")).isNull();
    assertThat(parse("https://kurl.me/ap/actors/")).isNull();
    assertThat(parse("https://kurl.me/ap/actors/abc/inbox")).isNull();
    assertThat(parse("")).isNull();
    assertThat(parse(null)).isNull();
  }
}
