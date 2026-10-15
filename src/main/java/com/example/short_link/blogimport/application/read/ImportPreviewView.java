package com.example.short_link.blogimport.application.read;

import java.time.Instant;
import java.util.List;

public record ImportPreviewView(
    String platform, String blogUrl, String blogTitle, boolean fullContent, List<Item> items) {

  public record Item(
      String title, String url, Instant publishedAt, String excerpt, String imageUrl) {}
}
