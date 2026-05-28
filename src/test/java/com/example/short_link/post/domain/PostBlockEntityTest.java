package com.example.short_link.post.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PostBlockEntityTest {

  @Test
  void constructorSetsFields() {
    PostBlockEntity b = new PostBlockEntity(10L, PostBlockType.PARAGRAPH, "hello", 0);
    assertThat(b.getPostId()).isEqualTo(10L);
    assertThat(b.getType()).isEqualTo(PostBlockType.PARAGRAPH);
    assertThat(b.getContent()).isEqualTo("hello");
    assertThat(b.getBlockOrder()).isZero();
  }

  @Test
  void belongsToMatchesPostId() {
    PostBlockEntity b = new PostBlockEntity(10L, PostBlockType.IMAGE, "{}", 1);
    assertThat(b.belongsTo(10L)).isTrue();
    assertThat(b.belongsTo(11L)).isFalse();
  }

  @Test
  void updateContentReplacesPayload() {
    PostBlockEntity b = new PostBlockEntity(10L, PostBlockType.PARAGRAPH, "v1", 0);
    b.updateContent("v2");
    assertThat(b.getContent()).isEqualTo("v2");
  }

  @Test
  void updateBlockOrder() {
    PostBlockEntity b = new PostBlockEntity(10L, PostBlockType.DIVIDER, null, 0);
    b.updateBlockOrder(5);
    assertThat(b.getBlockOrder()).isEqualTo(5);
  }

  @Test
  void ctaRefBlockSupportedAsType() {
    PostBlockEntity b = new PostBlockEntity(10L, PostBlockType.CTA_REF, "{\"ctaId\":42}", 2);
    assertThat(b.getType()).isEqualTo(PostBlockType.CTA_REF);
    assertThat(b.getContent()).isEqualTo("{\"ctaId\":42}");
  }
}
