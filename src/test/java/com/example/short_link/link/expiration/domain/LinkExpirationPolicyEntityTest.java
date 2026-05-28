package com.example.short_link.link.expiration.domain;

import static org.assertj.core.api.Assertions.assertThat;

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

  @Test
  void changeBlockedCountriesAcceptsCsvAndClearsOnBlank() {
    LinkExpirationPolicyEntity entity = new LinkExpirationPolicyEntity(new LinkId(1L));

    entity.changeBlockedCountries("KR,JP");
    assertThat(entity.getBlockedCountries()).isEqualTo("KR,JP");

    entity.changeBlockedCountries("  ");
    assertThat(entity.getBlockedCountries()).isNull();

    entity.changeBlockedCountries(null);
    assertThat(entity.getBlockedCountries()).isNull();
  }

  @Test
  void changeExpiredMessageTruncatesAndNormalizesBlank() {
    LinkExpirationPolicyEntity entity = new LinkExpirationPolicyEntity(new LinkId(1L));

    entity.changeExpiredMessage("  hello  ");
    assertThat(entity.getExpiredMessage()).isEqualTo("hello");

    entity.changeExpiredMessage("");
    assertThat(entity.getExpiredMessage()).isNull();

    entity.changeExpiredMessage(null);
    assertThat(entity.getExpiredMessage()).isNull();

    entity.changeExpiredMessage("x".repeat(600));
    assertThat(entity.getExpiredMessage()).hasSize(500);
  }

  @Test
  void changeExpiredRedirectUrlTrimsAndNormalizesBlank() {
    LinkExpirationPolicyEntity entity = new LinkExpirationPolicyEntity(new LinkId(1L));

    entity.changeExpiredRedirectUrl("  https://example.com  ");
    assertThat(entity.getExpiredRedirectUrl()).isEqualTo("https://example.com");

    entity.changeExpiredRedirectUrl("");
    assertThat(entity.getExpiredRedirectUrl()).isNull();

    entity.changeExpiredRedirectUrl(null);
    assertThat(entity.getExpiredRedirectUrl()).isNull();
  }
}
