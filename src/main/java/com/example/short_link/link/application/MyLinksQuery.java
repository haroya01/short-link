package com.example.short_link.link.application;

public record MyLinksQuery(int page, int size, String q) {

  public static final int DEFAULT_SIZE = 20;
  public static final int MAX_SIZE = 100;

  public static MyLinksQuery of(Integer page, Integer size, String q) {
    int p = page == null || page < 1 ? 1 : page;
    int s = size == null || size < 1 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
    String trimmed = q == null ? null : q.trim();
    String query = trimmed == null || trimmed.isEmpty() ? null : trimmed;
    return new MyLinksQuery(p, s, query);
  }

  public int zeroBasedPage() {
    return page - 1;
  }
}
