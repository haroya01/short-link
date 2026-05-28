package com.example.short_link.post.application.read;

import com.example.short_link.post.domain.PostBlockEntity;

public record PostBlockView(Long id, String type, String content, Integer blockOrder) {

  public static PostBlockView from(PostBlockEntity block) {
    return new PostBlockView(
        block.getId(), block.getType().name(), block.getContent(), block.getBlockOrder());
  }
}
