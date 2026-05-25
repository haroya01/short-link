package com.example.short_link.link.presentation.response;

import com.example.short_link.link.application.dto.LinkEventView;
import java.time.Instant;

public record LinkEventResponse(
    Instant clickedAt,
    String country,
    String region,
    String city,
    String device,
    String os,
    String browser,
    String referrer,
    String referrerHost,
    String channel,
    String language,
    boolean bot,
    String botName,
    String ipMasked) {

  public static LinkEventResponse from(LinkEventView item) {
    return new LinkEventResponse(
        item.clickedAt(),
        item.country(),
        item.region(),
        item.city(),
        item.device(),
        item.os(),
        item.browser(),
        item.referrer(),
        item.referrerHost(),
        item.channel(),
        item.language(),
        item.bot(),
        item.botName(),
        item.ipMasked());
  }
}
