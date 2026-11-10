package com.example.short_link.link.presentation.response;

import com.example.short_link.link.application.dto.AdminLinkClickView;

public record AdminLinkClickResponse(long id, boolean excluded, LinkEventResponse click) {

  public static AdminLinkClickResponse from(AdminLinkClickView item) {
    return new AdminLinkClickResponse(
        item.id(), item.excluded(), LinkEventResponse.from(item.click()));
  }
}
