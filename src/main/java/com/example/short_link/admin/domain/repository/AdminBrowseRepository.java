package com.example.short_link.admin.domain.repository;

import com.example.short_link.admin.domain.repository.AdminMetricsRepository.StatPage;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.moderation.domain.LinkDisableReason;
import com.example.short_link.user.domain.UserEntity;
import java.time.Instant;
import java.util.Optional;

public interface AdminBrowseRepository {

  StatPage<UserRow> findUsers(String q, String role, int page, int size);

  Optional<UserRow> findUser(long userId);

  StatPage<LinkRow> findLinks(String q, Long ownerId, LinkSort sort, int page, int size);

  Optional<LinkRow> findLink(ShortCode shortCode);

  enum LinkSort {
    RECENT,
    CLICKS
  }

  interface UserRow {
    Long getId();

    String getEmail();

    String getUsername();

    UserEntity.Role getRole();

    Instant getCreatedAt();

    Instant getDeletedAt();

    Long getLinkCount();
  }

  interface LinkRow {
    String getShortCode();

    String getOriginalUrl();

    Long getOwnerId();

    String getOwnerEmail();

    Long getClickCount();

    Instant getCreatedAt();

    Instant getExpiresAt();

    Integer getMaxViews();

    Integer getViewCount();

    Integer getPasswordProtected();

    LinkDisableReason getDisabledReason();

    Instant getDisabledAt();
  }
}
