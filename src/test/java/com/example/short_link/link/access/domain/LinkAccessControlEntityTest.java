package com.example.short_link.link.access.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.link.domain.LinkEntity;
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
  void mirrorsThePasswordHashAndViewLimitTheLinkKeeps() {
    LinkAccessControlEntity entity = new LinkAccessControlEntity(new LinkId(1L));
    LinkEntity link = new LinkEntity("https://target", "abc1234", 7L, null);

    link.setPasswordHash("$2a$10$xx");
    link.setMaxViews(50);
    entity.mirrorPasswordHash(link);
    entity.mirrorMaxViews(link);
    assertThat(entity.getPasswordHash()).isEqualTo("$2a$10$xx");
    assertThat(entity.getMaxViews()).isEqualTo(50);

    link.setPasswordHash("");
    link.setMaxViews(null);
    entity.mirrorPasswordHash(link);
    entity.mirrorMaxViews(link);
    assertThat(entity.hasPassword()).isFalse();
    assertThat(entity.getMaxViews()).isNull();
  }
}
