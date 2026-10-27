package com.example.short_link.link.application.dto;

import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.LinkId;
import com.example.short_link.link.domain.ShortCode;

public record LinkRef(LinkId linkId, Long userId, ShortCode shortCode, String originalUrl) {

  public static LinkRef of(LinkEntity link) {
    return new LinkRef(link.linkId(), link.getUserId(), link.getShortCode(), link.getOriginalUrl());
  }
}
