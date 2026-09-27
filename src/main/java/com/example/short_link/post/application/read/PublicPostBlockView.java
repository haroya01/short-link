package com.example.short_link.post.application.read;

import com.example.short_link.post.domain.PostBlockContent;
import com.example.short_link.post.domain.PostBlockEntity;

public record PublicPostBlockView(String type, String content, Integer blockOrder, CtaInfo cta)
    implements PostBlockContent {

  public static PublicPostBlockView from(PostBlockEntity block) {
    return new PublicPostBlockView(
        block.getType().name(), block.getContent(), block.getBlockOrder(), null);
  }

  public static PublicPostBlockView fromWithCta(PostBlockEntity block, CtaInfo cta) {
    return new PublicPostBlockView(
        block.getType().name(), block.getContent(), block.getBlockOrder(), cta);
  }

  public record CtaInfo(String label, String url, String style, String purpose, boolean deleted) {}
}
