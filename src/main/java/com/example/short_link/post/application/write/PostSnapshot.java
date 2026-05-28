package com.example.short_link.post.application.write;

import java.util.List;

public record PostSnapshot(
    String title,
    String excerpt,
    String ogImageUrl,
    String ogImageKey,
    String languageTag,
    List<BlockSnapshot> blocks) {

  public record BlockSnapshot(String type, String content) {}
}
