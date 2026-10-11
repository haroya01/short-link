package com.example.short_link.post.application.write;

import java.util.List;
import java.util.Objects;

public record PostSnapshot(
    String title,
    String excerpt,
    String ogImageUrl,
    String ogImageKey,
    Boolean coverChosen,
    String languageTag,
    List<BlockSnapshot> blocks) {

  public record BlockSnapshot(String type, String content) {}

  // coverChosen 이 생기기 전 리비전에는 값이 없다. 그 경우엔 나머지가 같으면 같은 내용이다.
  boolean sameContentAs(PostSnapshot other) {
    return other != null
        && Objects.equals(title, other.title)
        && Objects.equals(excerpt, other.excerpt)
        && Objects.equals(ogImageUrl, other.ogImageUrl)
        && Objects.equals(ogImageKey, other.ogImageKey)
        && (coverChosen == null
            || other.coverChosen == null
            || coverChosen.equals(other.coverChosen))
        && Objects.equals(languageTag, other.languageTag)
        && Objects.equals(blocks, other.blocks);
  }
}
