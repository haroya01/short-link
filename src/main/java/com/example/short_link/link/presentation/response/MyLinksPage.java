package com.example.short_link.link.presentation.response;

import com.example.short_link.link.application.ShortLinkUrlBuilder;
import com.example.short_link.link.application.dto.MyLinksResult;
import java.util.List;

public record MyLinksPage(List<MyLinkResponse> items, String nextCursor, boolean hasMore) {

  public static MyLinksPage from(MyLinksResult result, ShortLinkUrlBuilder urlBuilder) {
    return new MyLinksPage(
        result.items().stream().map(my -> MyLinkResponse.from(my, urlBuilder)).toList(),
        result.nextCursor(),
        result.hasMore());
  }
}
