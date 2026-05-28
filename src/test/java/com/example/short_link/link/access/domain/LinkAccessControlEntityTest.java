package com.example.short_link.link.access.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.link.domain.LinkId;
import org.junit.jupiter.api.Test;

class LinkAccessControlEntityTest {

  @Test
  void nullLinkIdYieldsNullTypedAccessor() {
    LinkAccessControlEntity entity = new LinkAccessControlEntity((LinkId) null);
    assertThat(entity.linkId()).isNull();
  }

  @Test
  void linkIdRoundTrips() {
    LinkAccessControlEntity entity = new LinkAccessControlEntity(new LinkId(9L));
    assertThat(entity.linkId()).isEqualTo(new LinkId(9L));
  }

  @Test
  void changePasswordHashAcceptsValueAndClearsOnBlank() {
    LinkAccessControlEntity entity = new LinkAccessControlEntity(new LinkId(1L));

    entity.changePasswordHash("$2a$10$xx");
    assertThat(entity.getPasswordHash()).isEqualTo("$2a$10$xx");
    assertThat(entity.hasPassword()).isTrue();

    entity.changePasswordHash("");
    assertThat(entity.getPasswordHash()).isNull();
    assertThat(entity.hasPassword()).isFalse();

    entity.changePasswordHash(null);
    assertThat(entity.getPasswordHash()).isNull();
  }

  @Test
  void changeMaxViewsAcceptsAnyInteger() {
    LinkAccessControlEntity entity = new LinkAccessControlEntity(new LinkId(1L));

    entity.changeMaxViews(50);
    assertThat(entity.getMaxViews()).isEqualTo(50);

    entity.changeMaxViews(null);
    assertThat(entity.getMaxViews()).isNull();
  }
}
