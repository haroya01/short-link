package com.example.short_link.link.presentation.response;

import com.example.short_link.link.application.ShortLinkUrlBuilder;
import com.example.short_link.link.application.dto.MyLink;
import java.time.Instant;
import java.util.List;

public record MyLinkResponse(
    String shortCode,
    String shortUrl,
    String originalUrl,
    Instant createdAt,
    Instant expiresAt,
    long clickCount,
    List<String> tags,
    List<Long> clicksLast7d) {

  public static MyLinkResponse from(MyLink my, ShortLinkUrlBuilder urlBuilder) {
    return new MyLinkResponse(
        my.shortCode(),
        urlBuilder.build(my.shortCode()),
        my.originalUrl(),
        my.createdAt(),
        my.expiresAt(),
        my.clickCount(),
        my.tags(),
        my.clicksLast7d());
  }
}
