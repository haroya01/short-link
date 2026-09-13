package com.example.short_link.post.presentation.request;

import com.example.short_link.post.application.read.PublicFeedQuery;
import lombok.Builder;

@Builder
public record PublicFeedRequest(String sort, String tag, String q, String lang) {

  public PublicFeedQuery toQuery(int page, int size) {
    return PublicFeedQuery.from(q, tag, sort, lang, page, size);
  }
}
