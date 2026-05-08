package com.example.short_link.link.application;

public record MyLinksQuery(int page, int size, String q, String tag) {

  public static final int DEFAULT_SIZE = 20;
  public static final int MAX_SIZE = 100;

  public static MyLinksQuery of(Integer page, Integer size, String q, String tag) {
    int p = page == null || page < 1 ? 1 : page;
    int s = size == null || size < 1 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
    String trimmedQ = q == null ? null : q.trim();
    String query = trimmedQ == null || trimmedQ.isEmpty() ? null : trimmedQ;
    String trimmedTag = tag == null ? null : tag.trim();
    String tagFilter = trimmedTag == null || trimmedTag.isEmpty() ? null : trimmedTag;
    return new MyLinksQuery(p, s, query, tagFilter);
  }

  public int zeroBasedPage() {
    return page - 1;
  }
}
