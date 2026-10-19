package com.example.short_link.common.discovery;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "short-link.trending")
public record TrendingSpread(int perAuthor) {

  public TrendingSpread {
    perAuthor = perAuthor <= 0 ? 2 : perAuthor;
  }
}
