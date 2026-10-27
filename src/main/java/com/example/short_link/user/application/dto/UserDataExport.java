package com.example.short_link.user.application.dto;

import com.example.short_link.link.application.LinkAccountData.ExportedClick;
import com.example.short_link.link.application.LinkAccountData.ExportedLink;
import com.example.short_link.user.domain.UserEntity;
import java.time.Instant;
import java.util.List;

public record UserDataExport(
    ExportedUser user, List<ExportedLink> links, List<ExportedClick> clickEvents) {

  public record ExportedUser(
      Long id,
      String email,
      String oauthProvider,
      String role,
      String timezone,
      Instant createdAt) {

    public static ExportedUser from(UserEntity u) {
      return new ExportedUser(
          u.getId(),
          u.getEmail(),
          u.getOauthProvider(),
          u.getRole().name(),
          u.getTimezone(),
          u.getCreatedAt());
    }
  }
}
