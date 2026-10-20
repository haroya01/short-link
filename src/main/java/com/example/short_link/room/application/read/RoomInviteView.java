package com.example.short_link.room.application.read;

import com.example.short_link.room.domain.RoomInviteEntity;
import com.example.short_link.room.domain.RoomPerson;
import java.time.Instant;

public record RoomInviteView(
    Long id,
    String code,
    String url,
    String pageUrl,
    Integer maxUses,
    int uses,
    boolean approval,
    Instant expiresAt,
    boolean disabled,
    RoomPerson creator) {

  public static RoomInviteView of(
      RoomInviteEntity invite, RoomInviteUrls urls, RoomPerson creator) {
    return new RoomInviteView(
        invite.getId(),
        invite.getCode(),
        invite.getShortCode() == null ? null : urls.shortLink(invite.getShortCode()),
        urls.page(invite.getCode()),
        invite.getMaxUses(),
        invite.getUses(),
        invite.isApproval(),
        invite.getExpiresAt(),
        invite.getDisabledAt() != null,
        creator);
  }
}
