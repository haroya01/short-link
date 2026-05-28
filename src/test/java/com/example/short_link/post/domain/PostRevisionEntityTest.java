package com.example.short_link.post.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PostRevisionEntityTest {

  @Test
  void constructorSetsFields() {
    PostRevisionEntity r = new PostRevisionEntity(10L, 1, "Title v1", "{\"blocks\":[]}");
    assertThat(r.getPostId()).isEqualTo(10L);
    assertThat(r.getVersionNumber()).isEqualTo(1);
    assertThat(r.getTitleSnapshot()).isEqualTo("Title v1");
    assertThat(r.getContentJson()).isEqualTo("{\"blocks\":[]}");
  }

  @Test
  void belongsToMatchesPostId() {
    PostRevisionEntity r = new PostRevisionEntity(10L, 1, "Title", "{}");
    assertThat(r.belongsTo(10L)).isTrue();
    assertThat(r.belongsTo(11L)).isFalse();
  }
}
