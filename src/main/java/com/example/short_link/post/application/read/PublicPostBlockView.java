package com.example.short_link.post.application.read;

import com.example.short_link.post.domain.PostBlockEntity;

/** Public-safe block — type + content. id 는 내부용이라 노출 X. */
public record PublicPostBlockView(String type, String content, Integer blockOrder) {

  public static PublicPostBlockView from(PostBlockEntity block) {
    return new PublicPostBlockView(
        block.getType().name(), block.getContent(), block.getBlockOrder());
  }
}
