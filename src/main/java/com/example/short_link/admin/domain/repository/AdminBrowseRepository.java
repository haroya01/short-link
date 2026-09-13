package com.example.short_link.admin.domain.repository;

import com.example.short_link.admin.domain.repository.AdminMetricsRepository.StatPage;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.user.domain.UserEntity;
import java.time.Instant;
import java.util.Optional;

/** 검색 필터는 정규화된 값을 받으며, null은 해당 필터를 적용하지 않는다는 뜻이다. */
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
  }
}
