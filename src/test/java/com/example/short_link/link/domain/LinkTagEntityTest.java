package com.example.short_link.link.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.link.domain.LinkTagEntity.LinkTagId;
import org.junit.jupiter.api.Test;

class LinkTagEntityTest {

  @Test
  void constructorSetsIds() {
    LinkTagEntity e = new LinkTagEntity(1L, 2L);
    assertThat(e.getLinkId()).isEqualTo(1L);
    assertThat(e.getTagId()).isEqualTo(2L);
  }

  @Test
  void linkTagIdEqualsAndHashCode() {
    LinkTagId a = new LinkTagId(1L, 2L);
    LinkTagId b = new LinkTagId(1L, 2L);
    LinkTagId c = new LinkTagId(1L, 3L);
    LinkTagId d = new LinkTagId(2L, 2L);
    assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
    assertThat(a).isNotEqualTo(c);
    assertThat(a).isNotEqualTo(d);
    assertThat(a).isNotEqualTo("string");
    assertThat(a).isEqualTo(a);
    assertThat(a).isNotEqualTo(null);
    assertThat(new LinkTagId()).isNotEqualTo(a);
  }
}
