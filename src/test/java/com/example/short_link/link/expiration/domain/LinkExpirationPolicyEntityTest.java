package com.example.short_link.link.expiration.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.LinkId;
import org.junit.jupiter.api.Test;

class LinkExpirationPolicyEntityTest {

  @Test
  void nullLinkIdYieldsNullTypedAccessor() {
    LinkExpirationPolicyEntity entity = new LinkExpirationPolicyEntity((LinkId) null);
    assertThat(entity.linkId()).isNull();
  }

  @Test
  void linkIdRoundTrips() {
    LinkExpirationPolicyEntity entity = new LinkExpirationPolicyEntity(new LinkId(7L));
    assertThat(entity.linkId()).isEqualTo(new LinkId(7L));
  }

  private final LinkEntity link = new LinkEntity("https://target", "abc1234", 7L, null);
  private final LinkExpirationPolicyEntity entity = new LinkExpirationPolicyEntity(new LinkId(1L));

  @Test
  void mirrorsTheBlockedCountriesTheLinkKeeps() {
    link.setBlockedCountries("KR,JP");
    entity.mirrorBlockedCountries(link);
    assertThat(entity.getBlockedCountries()).isEqualTo("KR,JP");

    link.setBlockedCountries("  ");
    entity.mirrorBlockedCountries(link);
    assertThat(entity.getBlockedCountries()).isNull();

    link.setBlockedCountries(null);
    entity.mirrorBlockedCountries(link);
    assertThat(entity.getBlockedCountries()).isNull();
  }

  @Test
  void mirrorsTheExpiredMessageTrimmedAndCutAsTheLinkKeepsIt() {
    link.updateExpiredMessage("  hello  ");
    entity.mirrorExpiredMessage(link);
    assertThat(entity.getExpiredMessage()).isEqualTo("hello");

    link.updateExpiredMessage("");
    entity.mirrorExpiredMessage(link);
    assertThat(entity.getExpiredMessage()).isNull();

    link.updateExpiredMessage(null);
    entity.mirrorExpiredMessage(link);
    assertThat(entity.getExpiredMessage()).isNull();

    link.updateExpiredMessage("x".repeat(600));
    entity.mirrorExpiredMessage(link);
    assertThat(entity.getExpiredMessage()).hasSize(500);
  }

  @Test
  void mirrorsTheExpiredRedirectUrlTrimmedAsTheLinkKeepsIt() {
    link.changeExpiredRedirectUrl("  https://example.com  ");
    entity.mirrorExpiredRedirectUrl(link);
    assertThat(entity.getExpiredRedirectUrl()).isEqualTo("https://example.com");

    link.changeExpiredRedirectUrl("");
    entity.mirrorExpiredRedirectUrl(link);
    assertThat(entity.getExpiredRedirectUrl()).isNull();

    link.changeExpiredRedirectUrl(null);
    entity.mirrorExpiredRedirectUrl(link);
    assertThat(entity.getExpiredRedirectUrl()).isNull();
  }
}
