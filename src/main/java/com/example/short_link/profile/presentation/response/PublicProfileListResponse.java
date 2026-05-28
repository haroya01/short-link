package com.example.short_link.profile.presentation.response;

import com.example.short_link.profile.application.read.ProfileQueryService.PublicHandlesPage;
import java.util.List;

public record PublicProfileListResponse(List<PublicProfileHandleItem> items, long total) {

  public static PublicProfileListResponse from(PublicHandlesPage page) {
    return new PublicProfileListResponse(
        page.handles().stream().map(PublicProfileHandleItem::new).toList(), page.total());
  }
}
