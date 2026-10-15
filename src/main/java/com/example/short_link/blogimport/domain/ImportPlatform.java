package com.example.short_link.blogimport.domain;

public enum ImportPlatform {
  NAVER(true),
  TISTORY(false),
  VELOG(false),
  BRUNCH(true);

  private final boolean excerptOnlyFeed;

  ImportPlatform(boolean excerptOnlyFeed) {
    this.excerptOnlyFeed = excerptOnlyFeed;
  }

  public boolean excerptOnlyFeed() {
    return excerptOnlyFeed;
  }
}
