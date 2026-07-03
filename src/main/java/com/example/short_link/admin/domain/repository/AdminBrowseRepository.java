package com.example.short_link.admin.domain.repository;

import com.example.short_link.admin.domain.repository.AdminMetricsRepository.StatPage;
import com.example.short_link.user.domain.UserEntity;
import java.time.Instant;
import java.util.Optional;

/**
 * Full-table browse over users and links for the admin console — the operational counterpart to the
 * aggregate reads in {@link AdminMetricsRepository}. Every method is a paginated read; {@code q} is
 * an already-normalized filter ({@code null} means "no filter") so this port stays free of
 * request-shaping concerns. Reuses {@link StatPage} for the {@code items + total} envelope.
 */
public interface AdminBrowseRepository {

  StatPage<UserRow> findUsers(String q, String role, int page, int size);

  Optional<UserRow> findUser(long userId);

  StatPage<LinkRow> findLinks(String q, Long ownerId, int page, int size);

  interface UserRow {
    Long getId();

    String getEmail();

    String getUsername();

    UserEntity.Role getRole();

    UserEntity.Tier getTier();

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
