package com.example.short_link.post.application.read;

import com.example.short_link.post.domain.PostBlockEntity;

/**
 * Public-safe block. type / content / order 외에 CTA_REF 블록은 hydrated CTA 정보가 함께 옴. Non-CTA 블록은 cta =
 * null. 프런트엔드는 type === 'CTA_REF' 이면 cta 사용, 아니면 content 사용.
 */
public record PublicPostBlockView(String type, String content, Integer blockOrder, CtaInfo cta) {

  public static PublicPostBlockView from(PostBlockEntity block) {
    return new PublicPostBlockView(
        block.getType().name(), block.getContent(), block.getBlockOrder(), null);
  }

  public static PublicPostBlockView fromWithCta(PostBlockEntity block, CtaInfo cta) {
    return new PublicPostBlockView(
        block.getType().name(), block.getContent(), block.getBlockOrder(), cta);
  }

  /** CTA 라이브러리 entity 의 public-safe view. label / url / style / purpose + deleted flag. */
  public record CtaInfo(String label, String url, String style, String purpose, boolean deleted) {}
}
